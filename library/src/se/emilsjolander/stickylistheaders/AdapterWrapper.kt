package se.emilsjolander.stickylistheaders

import android.content.Context
import android.database.DataSetObserver
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Checkable
import android.widget.ListAdapter
import java.util.*

/**
 * A [ListAdapter] which wraps a [StickyListHeadersAdapter] and
 * automatically handles wrapping the result of
 * [StickyListHeadersAdapter.getView]
 * and
 * [StickyListHeadersAdapter.getHeaderView]
 * appropriately.
 *
 * @author Jake Wharton (jakewharton@gmail.com)
 */
internal open class AdapterWrapper(
    private val mContext: Context,
    var mDelegate: StickyListHeadersAdapter
) : BaseAdapter(), StickyListHeadersAdapter {
    private val mHeaderCache = LinkedList<View>()
    private var mDivider: Drawable? = null
    private var mDividerHeight: Int = 0
    private var mOnHeaderClickListener: OnHeaderClickListener? = null
    private val mDataSetObserver = object : DataSetObserver() {

        override fun onInvalidated() {
            mHeaderCache.clear()
            super@AdapterWrapper.notifyDataSetInvalidated()
        }

        override fun onChanged() {
            super@AdapterWrapper.notifyDataSetChanged()
        }
    }

    internal interface OnHeaderClickListener {
        fun onHeaderClick(header: View, itemPosition: Int, headerId: Long)
    }

    init {
        mDelegate.registerDataSetObserver(mDataSetObserver)
    }

    fun setDivider(divider: Drawable, dividerHeight: Int) {
        this.mDivider = divider
        this.mDividerHeight = dividerHeight
        notifyDataSetChanged()
    }

    override fun areAllItemsEnabled(): Boolean {
        return mDelegate.areAllItemsEnabled()
    }

    override fun isEnabled(position: Int): Boolean {
        return mDelegate.isEnabled(position)
    }

    override fun getCount(): Int {
        return mDelegate.count
    }

    override fun getItem(position: Int): Any {
        return mDelegate.getItem(position)
    }

    override fun getItemId(position: Int): Long {
        return mDelegate.getItemId(position)
    }

    override fun hasStableIds(): Boolean {
        return mDelegate.hasStableIds()
    }

    override fun getItemViewType(position: Int): Int {
        return mDelegate.getItemViewType(position)
    }

    override fun getViewTypeCount(): Int {
        return mDelegate.viewTypeCount
    }

    override fun isEmpty(): Boolean {
        return mDelegate.isEmpty
    }

    /**
     * Will recycle header from [WrapperView] if it exists
     */
    private fun recycleHeaderIfExists(wv: WrapperView) {
        val header = wv.mHeader
        if (header != null) {
            // reset the headers visibility when adding it to the cache
            header.visibility = View.VISIBLE
            mHeaderCache.add(header)
        }
    }

    /**
     * Get a header view. This optionally pulls a header from the supplied
     * [WrapperView] and will also recycle the divider if it exists.
     */
    private fun configureHeader(wv: WrapperView, position: Int): View {
        var header: View? = if (wv.mHeader == null) popHeader() else wv.mHeader
        header = mDelegate.getHeaderView(position, header, wv)
        //if the header isn't clickable, the listSelector will be drawn on top of the header
        header.isClickable = true
        header.setOnClickListener { v ->
            mOnHeaderClickListener?.let { listener ->
                val headerId = mDelegate.getHeaderId(position)
                listener.onHeaderClick(v, position, headerId)
            }
        }
        return header
    }

    private fun popHeader(): View? {
        return if (mHeaderCache.size > 0) {
            mHeaderCache.removeAt(0)
        } else null
    }

    /** Returns `true` if the previous position has the same header ID.  */
    private fun previousPositionHasSameHeader(position: Int): Boolean {
        return position != 0 && mDelegate.getHeaderId(position) == mDelegate
            .getHeaderId(position - 1)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): WrapperView {
        var wv = if (convertView == null) WrapperView(mContext) else convertView as WrapperView
        val item = mDelegate.getView(position, wv.mItem, parent)
        var header: View? = null
        if (previousPositionHasSameHeader(position)) {
            recycleHeaderIfExists(wv)
        } else {
            header = configureHeader(wv, position)
        }
        if (item is Checkable && wv !is CheckableWrapperView) {
            // Need to create Checkable subclass of WrapperView for ListView to work correctly
            wv = CheckableWrapperView(mContext)
        } else if (item !is Checkable && wv is CheckableWrapperView) {
            wv = WrapperView(mContext)
        }
        wv.update(item, header, mDivider, mDividerHeight)
        return wv
    }

    fun setOnHeaderClickListener(onHeaderClickListener: OnHeaderClickListener?) {
        this.mOnHeaderClickListener = onHeaderClickListener
    }

    override fun equals(o: Any?): Boolean {
        return mDelegate == o
    }

    override fun getDropDownView(position: Int, convertView: View, parent: ViewGroup): View {
        return (mDelegate as BaseAdapter).getDropDownView(position, convertView, parent)
    }

    override fun hashCode(): Int {
        return mDelegate.hashCode()
    }

    override fun notifyDataSetChanged() {
        (mDelegate as BaseAdapter).notifyDataSetChanged()
    }

    override fun notifyDataSetInvalidated() {
        (mDelegate as BaseAdapter).notifyDataSetInvalidated()
    }

    override fun toString(): String {
        return mDelegate.toString()
    }

    override fun getHeaderView(position: Int, convertView: View?, parent: ViewGroup): View {
        return mDelegate.getHeaderView(position, convertView, parent)
    }

    override fun getHeaderId(position: Int): Long {
        return mDelegate.getHeaderId(position)
    }

}
