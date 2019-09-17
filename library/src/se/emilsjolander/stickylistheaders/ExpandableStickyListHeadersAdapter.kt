package se.emilsjolander.stickylistheaders

import android.database.DataSetObserver
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import java.util.*


/**
 * @author lsjwzh
 */
class ExpandableStickyListHeadersAdapter(
    private val mInnerAdapter: StickyListHeadersAdapter
) : BaseAdapter(), StickyListHeadersAdapter {

    private var mViewToItemIdMap = DualHashMap<View, Long>()
    private var mHeaderIdToViewMap = DistinctMultiHashMap<Int, View>()
    private var mCollapseHeaderIds: MutableList<Long> = ArrayList()

    override fun getHeaderView(position: Int, convertView: View?, parent: ViewGroup): View {
        return mInnerAdapter.getHeaderView(position, convertView, parent)
    }

    override fun getHeaderId(position: Int): Long {
        return mInnerAdapter.getHeaderId(position)
    }

    override fun areAllItemsEnabled(): Boolean {
        return mInnerAdapter.areAllItemsEnabled()
    }

    override fun isEnabled(i: Int): Boolean {
        return mInnerAdapter.isEnabled(i)
    }

    override fun registerDataSetObserver(dataSetObserver: DataSetObserver) {
        mInnerAdapter.registerDataSetObserver(dataSetObserver)
    }

    override fun unregisterDataSetObserver(dataSetObserver: DataSetObserver) {
        mInnerAdapter.unregisterDataSetObserver(dataSetObserver)
    }

    override fun getCount(): Int {
        return mInnerAdapter.count
    }

    override fun getItem(i: Int): Any {
        return mInnerAdapter.getItem(i)
    }

    override fun getItemId(i: Int): Long {
        return mInnerAdapter.getItemId(i)
    }

    override fun hasStableIds(): Boolean {
        return mInnerAdapter.hasStableIds()
    }

    override fun getView(i: Int, view: View, viewGroup: ViewGroup): View {
        val convertView = mInnerAdapter.getView(i, view, viewGroup)
        mViewToItemIdMap.put(convertView, getItemId(i))
        mHeaderIdToViewMap.add(getHeaderId(i).toInt(), convertView)

        if (mCollapseHeaderIds.contains(getHeaderId(i))) {
            convertView.visibility = View.GONE
        } else {
            convertView.visibility = View.VISIBLE
        }
        return convertView
    }

    override fun getItemViewType(i: Int): Int {
        return mInnerAdapter.getItemViewType(i)
    }

    override fun getViewTypeCount(): Int {
        return mInnerAdapter.viewTypeCount
    }

    override fun isEmpty(): Boolean {
        return mInnerAdapter.isEmpty
    }

    fun getItemViewsByHeaderId(headerId: Long): List<View> {
        return mHeaderIdToViewMap.get(headerId.toInt())
    }

    fun isHeaderCollapsed(headerId: Long): Boolean {
        return mCollapseHeaderIds.contains(headerId)
    }

    fun expand(headerId: Long) {
        if (isHeaderCollapsed(headerId)) {
            mCollapseHeaderIds.remove(headerId)
        }
    }

    fun collapse(headerId: Long) {
        if (!isHeaderCollapsed(headerId)) {
            mCollapseHeaderIds.add(headerId)
        }
    }

    fun findViewByItemId(itemId: Long): View {
        return mViewToItemIdMap.getKey(itemId)
    }

    fun findItemIdByView(view: View): Long {
        return mViewToItemIdMap.get(view)
    }
}
