package se.emilsjolander.stickylistheaders

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup

/**
 *
 * the view that wrapps a divider header and a normal list item. The listview sees this as 1 item
 *
 * @author Emil Sjölander
 */
open class WrapperView internal constructor(c: Context) : ViewGroup(c) {

    var item: View? = null
    private var mDivider: Drawable? = null
    private var mDividerHeight: Int = 0
    var header: View? = null
        internal set
    internal var mItemTop: Int = 0

    fun hasHeader(): Boolean {
        return header != null
    }

    internal fun update(item: View?, header: View?, divider: Drawable, dividerHeight: Int) {

        //every wrapperview must have a list item
        if (item == null) {
            throw NullPointerException("List view item must not be null.")
        }

        //only remove the current item if it is not the same as the new item. this can happen if wrapping a recycled view
        if (this.item !== item) {
            removeView(this.item)
            this.item = item
            val parent = item.parent
            if (parent != null && parent !== this) {
                if (parent is ViewGroup) {
                    parent.removeView(item)
                }
            }
            addView(item)
        }

        //same logik as above but for the header
        if (this.header !== header) {
            if (this.header != null) {
                removeView(this.header)
            }
            this.header = header
            if (header != null) {
                addView(header)
            }
        }

        if (this.mDivider !== divider) {
            this.mDivider = divider
            this.mDividerHeight = dividerHeight
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
            measuredWidth,
            MeasureSpec.EXACTLY
        )
        var measuredHeight = 0

        //measure header or divider. when there is a header visible it acts as the divider
        if (header != null) {
            val params = header?.layoutParams
            if (params != null && params.height > 0) {
                header?.measure(
                    childWidthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(params.height, MeasureSpec.EXACTLY)
                )
            } else {
                header?.measure(
                    childWidthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                )
            }
            header?.let { measuredHeight += it.measuredHeight }

        } else if (mDivider != null && item?.visibility != View.GONE) {
            measuredHeight += mDividerHeight
        }

        //measure item
        item?.let { item ->
            val params = item.layoutParams
            if (item.visibility == View.GONE) {
                item.measure(
                    childWidthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY)
                )
            } else if (params != null && params.height >= 0) {
                item.measure(
                    childWidthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(params.height, MeasureSpec.EXACTLY)
                )
                measuredHeight += item.measuredHeight
            } else {
                item.measure(
                    childWidthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                )
                measuredHeight += item.measuredHeight
            }
        }

        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val l = 0
        val t = 0
        val r = width
        val b = height

        when {
            header != null -> {
                val headerHeight = header?.measuredHeight ?: 0
                header?.layout(l, t, r, headerHeight)
                mItemTop = headerHeight
                item?.layout(l, headerHeight, r, b)
            }
            mDivider != null -> {
                mDivider?.setBounds(l, t, r, mDividerHeight)
                mItemTop = mDividerHeight
                item?.layout(l, mDividerHeight, r, b)
            }
            else -> {
                mItemTop = t
                item?.layout(l, t, r, b)
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (header == null && mDivider != null && item?.visibility != View.GONE) {
            mDivider?.draw(canvas)
        }
    }
}
