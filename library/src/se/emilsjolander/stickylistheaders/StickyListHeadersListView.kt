package se.emilsjolander.stickylistheaders

import android.annotation.SuppressLint
import android.content.Context
import android.database.DataSetObserver
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AbsListView.OnScrollListener
import android.widget.AdapterView.OnItemClickListener
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.SectionIndexer
import se.emilsjolander.stickylistheaders.WrapperViewList.LifeCycleListener
import kotlin.math.abs
import kotlin.math.max

/**
 * Even though this is a FrameLayout subclass we still consider it a ListView.
 * This is because of 2 reasons:
 * 1. It acts like as ListView.
 * 2. It used to be a ListView subclass and refactoring the name would cause compatibility errors.
 *
 * @author Emil Sjölander
 * @modified RNies 2019
 */
open class StickyListHeadersListView constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyle: Int
) :
    FrameLayout(context, attrs, defStyle) {

    /* --- Children --- */
    private var mList: WrapperViewList = WrapperViewList(context)
    private var mHeader: View? = null

    /* --- Header state --- */
    private var mHeaderId = 0L
    private var mHeaderPosition = 0
    private var mHeaderOffset = 0

    /* --- Delegates --- */
    private val mOnScrollListenerDelegate: OnScrollListener? = null
    private var mAdapter: AdapterWrapper? = null

    /* --- Settings --- */
    private var mAreHeadersSticky = true
    private var mClippingToPadding = true
    private var mIsDrawingListUnderStickyHeader = true
    private var mPaddingLeft = 0
    private var mPaddingTop = 0
    private var mPaddingRight = 0
    private var mPaddingBottom = 0

    /* --- Touch handling --- */
    private var mDownY: Float = 0.toFloat()
    private var mHeaderOwnsTouch: Boolean = false
    private val mTouchSlop: Float = ViewConfiguration.get(getContext()).scaledTouchSlop.toFloat()

    /* --- Other --- */
    private val mOnHeaderClickListener: OnHeaderClickListener? = null
    private val mOnStickyHeaderOffsetChangedListener: OnStickyHeaderOffsetChangedListener? = null
    private var mOnStickyHeaderChangedListener: OnStickyHeaderChangedListener? = null
    private var mDataSetObserver: AdapterWrapperDataSetObserver? = null
    private var mDivider: Drawable? = null
    private var mDividerHeight: Int = 0

    /* ---------- ListView delegate methods ---------- */

    var adapter: StickyListHeadersAdapter?
        get() = if (mAdapter == null) null else mAdapter!!.mDelegate
        set(adapter) {
            if (adapter == null) {
                mList.adapter = null
                clearHeader()
                return
            }

            mAdapter?.unregisterDataSetObserver(mDataSetObserver)


            mAdapter = if (adapter is SectionIndexer) {
                SectionIndexerAdapterWrapper(context, adapter)
            } else {
                AdapterWrapper(context, adapter)
            }
            mDataSetObserver = AdapterWrapperDataSetObserver()
            mAdapter?.registerDataSetObserver(mDataSetObserver)

            if (mOnHeaderClickListener != null) {
                mAdapter?.setOnHeaderClickListener(AdapterWrapperHeaderClickHandler())
            } else {
                mAdapter?.setOnHeaderClickListener(null)
            }

            mDivider?.let { mAdapter?.setDivider(it, mDividerHeight) }

            mList.adapter = mAdapter
            clearHeader()
        }

    private val headerViewsCount: Int
        get() = mList.headerViewsCount

    val firstVisiblePosition: Int
        get() = mList.firstVisiblePosition

    val lastVisiblePosition: Int
        get() = mList.lastVisiblePosition

    val count: Int
        get() = mList.count

    interface OnHeaderClickListener {
        fun onHeaderClick(
            l: StickyListHeadersListView, header: View,
            itemPosition: Int, headerId: Long, currentlySticky: Boolean
        )
    }

    /**
     * Notifies the listener when the sticky headers top offset has changed.
     */
    interface OnStickyHeaderOffsetChangedListener {
        /**
         * @param l      The view parent
         * @param header The currently sticky header being offset.
         * This header is not guaranteed to have it's measurements set.
         * It is however guaranteed that this view has been measured,
         * therefor you should user getMeasured* methods instead of
         * get* methods for determining the view's size.
         * @param offset The amount the sticky header is offset by towards to top of the screen.
         */
        fun onStickyHeaderOffsetChanged(l: StickyListHeadersListView, header: View, offset: Int)
    }

    /**
     * Notifies the listener when the sticky header has been updated
     */
    interface OnStickyHeaderChangedListener {
        /**
         * @param l            The view parent
         * @param header       The new sticky header view.
         * @param itemPosition The position of the item within the adapter's data set of
         * the item whose header is now sticky.
         * @param headerId     The id of the new sticky header.
         */
        fun onStickyHeaderChanged(
            l: StickyListHeadersListView, header: View,
            itemPosition: Int, headerId: Long
        )

    }

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null) : this(
        context,
        attrs,
        R.attr.stickyListHeadersListViewStyle
    )

    init {

        // Initialize the wrapped list

        // null out divider, dividers are handled by adapter so they look good with headers
        mDivider = mList.divider
        mDividerHeight = mList.dividerHeight
        mList.divider = null
        mList.dividerHeight = 0

        if (attrs != null) {
            val a = context.theme.obtainStyledAttributes(
                attrs,
                R.styleable.StickyListHeadersListView,
                defStyle,
                0
            )

            try {
                // -- View attributes --
                val padding = a.getDimensionPixelSize(
                    R.styleable.StickyListHeadersListView_android_padding,
                    0
                )
                mPaddingLeft = a.getDimensionPixelSize(
                    R.styleable.StickyListHeadersListView_android_paddingLeft,
                    padding
                )
                mPaddingTop = a.getDimensionPixelSize(
                    R.styleable.StickyListHeadersListView_android_paddingTop,
                    padding
                )
                mPaddingRight = a.getDimensionPixelSize(
                    R.styleable.StickyListHeadersListView_android_paddingRight,
                    padding
                )
                mPaddingBottom = a.getDimensionPixelSize(
                    R.styleable.StickyListHeadersListView_android_paddingBottom,
                    padding
                )

                this.setPadding(mPaddingLeft, mPaddingTop, mPaddingRight, mPaddingBottom)

                // Set clip to padding on the list and reset value to default on
                // wrapper
                mClippingToPadding =
                    a.getBoolean(R.styleable.StickyListHeadersListView_android_clipToPadding, true)
                super.setClipToPadding(true)
                mList.clipToPadding = mClippingToPadding

                // scrollbars
                val scrollBars =
                    a.getInt(R.styleable.StickyListHeadersListView_android_scrollbars, 0x00000200)
                mList.isVerticalScrollBarEnabled = scrollBars and 0x00000200 != 0
                mList.isHorizontalScrollBarEnabled = scrollBars and 0x00000100 != 0

                // overScroll
                mList.overScrollMode =
                    a.getInt(R.styleable.StickyListHeadersListView_android_overScrollMode, 0)

                // -- ListView attributes --
                mList.setFadingEdgeLength(
                    a.getDimensionPixelSize(
                        R.styleable.StickyListHeadersListView_android_fadingEdgeLength,
                        mList.verticalFadingEdgeLength
                    )
                )
                val fadingEdge =
                    a.getInt(R.styleable.StickyListHeadersListView_android_requiresFadingEdge, 0)
                when (fadingEdge) {
                    0x00001000 -> {
                        mList.isVerticalFadingEdgeEnabled = false
                        mList.isHorizontalFadingEdgeEnabled = true
                    }
                    0x00002000 -> {
                        mList.isVerticalFadingEdgeEnabled = true
                        mList.isHorizontalFadingEdgeEnabled = false
                    }
                    else -> {
                        mList.isVerticalFadingEdgeEnabled = false
                        mList.isHorizontalFadingEdgeEnabled = false
                    }
                }
                mList.cacheColorHint = a
                    .getColor(
                        R.styleable.StickyListHeadersListView_android_cacheColorHint,
                        mList.cacheColorHint
                    )

                mList.choiceMode = a.getInt(
                    R.styleable.StickyListHeadersListView_android_choiceMode,
                    mList.choiceMode
                )

                mList.setDrawSelectorOnTop(
                    a.getBoolean(
                        R.styleable.StickyListHeadersListView_android_drawSelectorOnTop,
                        false
                    )
                )
                mList.isFastScrollEnabled = a.getBoolean(
                    R.styleable.StickyListHeadersListView_android_fastScrollEnabled,
                    mList.isFastScrollEnabled
                )

                mList.isFastScrollAlwaysVisible = a.getBoolean(
                    R.styleable.StickyListHeadersListView_android_fastScrollAlwaysVisible,
                    mList.isFastScrollAlwaysVisible
                )


                mList.scrollBarStyle =
                    a.getInt(R.styleable.StickyListHeadersListView_android_scrollbarStyle, 0)

                if (a.hasValue(R.styleable.StickyListHeadersListView_android_listSelector)) {
                    mList.selector =
                        a.getDrawable(R.styleable.StickyListHeadersListView_android_listSelector)
                }

                mList.isScrollingCacheEnabled = a.getBoolean(
                    R.styleable.StickyListHeadersListView_android_scrollingCache,
                    mList.isScrollingCacheEnabled
                )

                if (a.hasValue(R.styleable.StickyListHeadersListView_android_divider)) {
                    mDivider = a.getDrawable(R.styleable.StickyListHeadersListView_android_divider)
                }

                mList.isStackFromBottom =
                    a.getBoolean(
                        R.styleable.StickyListHeadersListView_android_stackFromBottom,
                        false
                    )

                mDividerHeight = a.getDimensionPixelSize(
                    R.styleable.StickyListHeadersListView_android_dividerHeight,
                    mDividerHeight
                )

                mList.transcriptMode = a.getInt(
                    R.styleable.StickyListHeadersListView_android_transcriptMode,
                    ListView.TRANSCRIPT_MODE_DISABLED
                )

                // -- StickyListHeaders attributes --
                mAreHeadersSticky =
                    a.getBoolean(R.styleable.StickyListHeadersListView_hasStickyHeaders, true)
                mIsDrawingListUnderStickyHeader = a.getBoolean(
                    R.styleable.StickyListHeadersListView_isDrawingListUnderStickyHeader,
                    true
                )
            } finally {
                a.recycle()
            }
        }

        // attach some listeners to the wrapped list
        mList.setLifeCycleListener(WrapperViewListLifeCycleListener())
        mList.setOnScrollListener(WrapperListScrollListener())

        this.addView(mList)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        measureHeader(mHeader)
    }

    private fun ensureHeaderHasCorrectLayoutParams(header: View) {
        var lp: ViewGroup.LayoutParams? = header.layoutParams
        if (lp == null) {
            lp = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            header.layoutParams = lp
        } else if (lp.height == LayoutParams.MATCH_PARENT || lp.width == LayoutParams.WRAP_CONTENT) {
            lp.height = LayoutParams.WRAP_CONTENT
            lp.width = LayoutParams.MATCH_PARENT
            header.layoutParams = lp
        }
    }

    private fun measureHeader(header: View?) {
        if (header != null) {
            val width = measuredWidth - mPaddingLeft - mPaddingRight
            val parentWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                width, MeasureSpec.EXACTLY
            )
            val parentHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
                0,
                MeasureSpec.UNSPECIFIED
            )
            measureChild(
                header, parentWidthMeasureSpec,
                parentHeightMeasureSpec
            )
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        mList.layout(0, 0, mList.measuredWidth, height)
        if (mHeader != null) {
            val lp = mHeader?.layoutParams as MarginLayoutParams
            val headerTop = lp.topMargin
            mHeader?.layout(
                mPaddingLeft,
                headerTop,
                mHeader?.measuredWidth?.plus(mPaddingLeft) ?: 0,
                mHeader?.measuredHeight?.let { headerTop.plus(it) } ?: 0
            )
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        // Only draw the list here.
        // The header should be drawn right after the lists children are drawn.
        // This is done so that the header is above the list items
        // but below the list decorators (scroll bars etc).
        if (mList.visibility == View.VISIBLE || mList.animation != null) {
            drawChild(canvas, mList, 0)
        }
    }

    // Reset values tied the header. also remove header form layout
    // This is called in response to the data set or the adapter changing
    private fun clearHeader() {
        if (mHeader != null) {
            removeView(mHeader)
            mHeader = null
            mHeaderId = 0
            mHeaderPosition = 0
            mHeaderOffset = 0

            // reset the top clipping length
            mList.setTopClippingLength(0)
            updateHeaderVisibilities()
        }
    }

    private fun updateOrClearHeader(firstVisiblePosition: Int) {
        val adapterCount = if (mAdapter == null) 0 else mAdapter?.count
        if (adapterCount == 0 || !mAreHeadersSticky) {
            return
        }

        val headerViewCount = mList.headerViewsCount
        var headerPosition = firstVisiblePosition - headerViewCount
        if (mList.childCount > 0) {
            val firstItem = mList.getChildAt(0)
            if (firstItem.bottom < stickyHeaderTop()) {
                headerPosition++
            }
        }

        // It is not a mistake to call getFirstVisiblePosition() here.
        // Most of the time getFixedFirstVisibleItem() should be called
        // but that does not work great together with getChildAt()
        val doesListHaveChildren = mList.childCount != 0
        val isFirstViewBelowTop = (doesListHaveChildren
                && mList.firstVisiblePosition == 0
                && mList.getChildAt(0).top >= stickyHeaderTop())
        val isHeaderPositionOutsideAdapterRange =
            headerPosition > adapterCount?.minus(1) ?: 0 || headerPosition < 0
        if (!doesListHaveChildren || isHeaderPositionOutsideAdapterRange || isFirstViewBelowTop) {
            clearHeader()
            return
        }

        updateHeader(headerPosition)
    }

    private fun updateHeader(headerPosition: Int) {

        // check if there is a new header should be sticky
        if (mHeaderPosition == 0 || mHeaderPosition != headerPosition) {
            mHeaderPosition = headerPosition
            val headerId = mAdapter?.getHeaderId(headerPosition) ?: 0L
            if (mHeaderId == 0L || mHeaderId != headerId) {
                mHeaderId = headerId
                val header = mAdapter?.getHeaderView(mHeaderPosition, mHeader, this)
                if (mHeader !== header) {
                    header?.let { swapHeader(it) }
                }
                ensureHeaderHasCorrectLayoutParams(mHeader!!)
                measureHeader(mHeader)
                if (mOnStickyHeaderChangedListener != null) {
                    mOnStickyHeaderChangedListener?.onStickyHeaderChanged(
                        this,
                        mHeader!!,
                        headerPosition,
                        mHeaderId
                    )
                }
                // Reset mHeaderOffset to null ensuring
                // that it will be set on the header and
                // not skipped for performance reasons.
                mHeaderOffset = 0
            }
        }

        var headerOffset = stickyHeaderTop()

        // Calculate new header offset
        // Skip looking at the first view. it never matters because it always
        // results in a headerOffset = 0
        for (i in 0 until mList.childCount) {
            val child = mList.getChildAt(i)
            val doesChildHaveHeader = child is WrapperView && child.hasHeader()
            val isChildFooter = mList.containsFooterView(child)

            if (child.top >= stickyHeaderTop() && (doesChildHaveHeader || isChildFooter)) {
                headerOffset =
                    (child.top.minus(mHeader?.measuredHeight ?: 0)).coerceAtMost(headerOffset)
                break
            }
        }

        setHeaderOffset(headerOffset)

        if (!mIsDrawingListUnderStickyHeader) {
            val h = mHeader?.measuredHeight ?: 0
            mList.setTopClippingLength(h.plus(mHeaderOffset))
        }

        updateHeaderVisibilities()
    }

    private fun swapHeader(newHeader: View) {
        if (mHeader != null) {
            removeView(mHeader)
        }
        mHeader = newHeader
        addView(mHeader)
        if (mOnHeaderClickListener != null) {
            mHeader?.setOnClickListener {
                mOnHeaderClickListener.onHeaderClick(
                    this@StickyListHeadersListView, mHeader!!,
                    mHeaderPosition, mHeaderId, true
                )
            }
        }
        mHeader?.isClickable = true
    }

    // hides the headers in the list under the sticky header.
    // Makes sure the other ones are showing
    private fun updateHeaderVisibilities() {
        val top = stickyHeaderTop()
        val childCount = mList.childCount
        for (i in 0 until childCount) {

            // ensure child is a wrapper view
            val child = mList.getChildAt(i)
            if (child !is WrapperView) {
                continue
            }

            // ensure wrapper view child has a header
            if (!child.hasHeader()) {
                continue
            }

            // update header views visibility
            val childHeader = child.header
            if (child.top < top) {
                if (childHeader?.visibility != View.INVISIBLE) {
                    childHeader?.visibility = View.INVISIBLE
                }
            } else {
                if (childHeader?.visibility != View.VISIBLE) {
                    childHeader?.visibility = View.VISIBLE
                }
            }
        }
    }

    // Wrapper around setting the header offset in different ways depending on
    // the API version
    @SuppressLint("NewApi")
    private fun setHeaderOffset(offset: Int) {
        if (mHeaderOffset == 0 || mHeaderOffset != offset) {
            mHeaderOffset = offset
            mHeader!!.translationY = mHeaderOffset.toFloat()

            mOnStickyHeaderOffsetChangedListener?.onStickyHeaderOffsetChanged(
                this,
                mHeader!!,
                (-mHeaderOffset)
            )
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.action and MotionEvent.ACTION_MASK
        if (action == MotionEvent.ACTION_DOWN) {
            mDownY = ev.y
            mHeaderOwnsTouch = mHeader != null && mDownY <= mHeader!!.height + mHeaderOffset
        }

        val handled: Boolean
        if (mHeaderOwnsTouch) {
            if (mHeader != null && abs(mDownY - ev.y) <= mTouchSlop) {
                handled = mHeader!!.dispatchTouchEvent(ev)
            } else {
                if (mHeader != null) {
                    val cancelEvent = MotionEvent.obtain(ev)
                    cancelEvent.action = MotionEvent.ACTION_CANCEL
                    mHeader!!.dispatchTouchEvent(cancelEvent)
                    cancelEvent.recycle()
                }

                val downEvent = MotionEvent.obtain(
                    ev.downTime,
                    ev.eventTime,
                    ev.action,
                    ev.x,
                    mDownY,
                    ev.metaState
                )
                downEvent.action = MotionEvent.ACTION_DOWN
                handled = mList.dispatchTouchEvent(downEvent)
                downEvent.recycle()
                mHeaderOwnsTouch = false
            }
        } else {
            handled = mList.dispatchTouchEvent(ev)
        }

        return handled
    }

    private inner class AdapterWrapperDataSetObserver : DataSetObserver() {

        override fun onChanged() {
            clearHeader()
        }

        override fun onInvalidated() {
            clearHeader()
        }

    }

    private inner class WrapperListScrollListener : OnScrollListener {

        override fun onScroll(
            view: AbsListView, firstVisibleItem: Int,
            visibleItemCount: Int, totalItemCount: Int
        ) {
            mOnScrollListenerDelegate?.onScroll(
                view, firstVisibleItem,
                visibleItemCount, totalItemCount
            )
            updateOrClearHeader(mList.fixedFirstVisibleItem)
        }

        override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {
            mOnScrollListenerDelegate?.onScrollStateChanged(
                view,
                scrollState
            )
        }

    }

    private inner class WrapperViewListLifeCycleListener : LifeCycleListener {

        override fun onDispatchDrawOccurred(canvas: Canvas) {
            if (mHeader != null) {
                if (mClippingToPadding) {
                    canvas.save()
                    canvas.clipRect(0, mPaddingTop, right, bottom)
                    drawChild(canvas, mHeader, 0)
                    canvas.restore()
                } else {
                    drawChild(canvas, mHeader, 0)
                }
            }
        }

    }

    private inner class AdapterWrapperHeaderClickHandler : AdapterWrapper.OnHeaderClickListener {

        override fun onHeaderClick(header: View, itemPosition: Int, headerId: Long) {
            mOnHeaderClickListener!!.onHeaderClick(
                this@StickyListHeadersListView, header, itemPosition,
                headerId, false
            )
        }
    }

    private fun isStartOfSection(position: Int): Boolean {
        return position == 0 || mAdapter?.getHeaderId(position) != mAdapter?.getHeaderId(position - 1)
    }

    private fun getHeaderOverlap(position: Int): Int {
        val isStartOfSection = isStartOfSection(max(0, position - headerViewsCount))
        if (!isStartOfSection) {
            val header = mAdapter?.getHeaderView(position, null, mList)

            header?.let { ensureHeaderHasCorrectLayoutParams(it) }
            measureHeader(header)
            return header?.measuredHeight ?: 0
        }
        return 0
    }

    private fun stickyHeaderTop(): Int {
        val mStickyHeaderTopOffset = 0
        return mStickyHeaderTopOffset + if (mClippingToPadding) mPaddingTop else 0
    }

    fun setOnStickyHeaderChangedListener(listener: OnStickyHeaderChangedListener) {
        mOnStickyHeaderChangedListener = listener
    }

    fun getListChildAt(index: Int): View {
        return mList.getChildAt(index)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun setOnTouchListener(l: OnTouchListener?) {
        if (l != null) {
            mList.setOnTouchListener { v, event ->
                l.onTouch(
                    this@StickyListHeadersListView,
                    event
                )
            }
        } else {
            mList.setOnTouchListener(null)
        }
    }

    fun setOnItemClickListener(listener: OnItemClickListener) {
        mList.onItemClickListener = listener
    }

    override fun isVerticalScrollBarEnabled(): Boolean {
        return mList.isVerticalScrollBarEnabled
    }

    override fun isHorizontalScrollBarEnabled(): Boolean {
        return mList.isHorizontalScrollBarEnabled
    }

    override fun setVerticalScrollBarEnabled(verticalScrollBarEnabled: Boolean) {
        mList.isVerticalScrollBarEnabled = verticalScrollBarEnabled
    }

    override fun setHorizontalScrollBarEnabled(horizontalScrollBarEnabled: Boolean) {
        mList.isHorizontalScrollBarEnabled = horizontalScrollBarEnabled
    }

    fun setSelection(position: Int) {
        setSelectionFromTop(position, 0)
    }

    private fun setSelectionFromTop(position: Int, y: Int) {
        var y = y
        y += if (mAdapter == null) 0 else getHeaderOverlap(position)
        y -= if (mClippingToPadding) 0 else mPaddingTop
        mList.setSelectionFromTop(position, y)
    }

    override fun setOnCreateContextMenuListener(l: OnCreateContextMenuListener) {
        mList.setOnCreateContextMenuListener(l)
    }

    override fun showContextMenu(): Boolean {
        return mList.showContextMenu()
    }

    override fun setClipToPadding(clipToPadding: Boolean) {
        mList.clipToPadding = clipToPadding
        mClippingToPadding = clipToPadding
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        mPaddingLeft = left
        mPaddingTop = top
        mPaddingRight = right
        mPaddingBottom = bottom

        mList.setPadding(left, top, right, bottom)
        super.setPadding(0, 0, 0, 0)
        requestLayout()
    }

    override fun getPaddingLeft(): Int {
        return mPaddingLeft
    }

    override fun getPaddingTop(): Int {
        return mPaddingTop
    }

    override fun getPaddingRight(): Int {
        return mPaddingRight
    }

    override fun getPaddingBottom(): Int {
        return mPaddingBottom
    }

    override fun setScrollBarStyle(style: Int) {
        mList.scrollBarStyle = style
    }

    override fun getScrollBarStyle(): Int {
        return mList.scrollBarStyle
    }

    public override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        check(!(superState !== BaseSavedState.EMPTY_STATE)) { "Handling non empty state of parent class is not implemented" }
        return mList.onSaveInstanceState()
    }

    public override fun onRestoreInstanceState(state: Parcelable) {
        super.onRestoreInstanceState(BaseSavedState.EMPTY_STATE)
        mList.onRestoreInstanceState(state)
    }

}
