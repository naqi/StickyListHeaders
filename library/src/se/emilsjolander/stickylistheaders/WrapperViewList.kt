package se.emilsjolander.stickylistheaders

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.widget.AbsListView
import android.widget.ListView
import java.lang.reflect.Field
import java.util.*

internal class WrapperViewList(context: Context) : ListView(context) {

    private var mLifeCycleListener: LifeCycleListener? = null
    private var mFooterViews: MutableList<View>? = null
    private var mTopClippingLength: Int = 0
    private var mSelectorRect = Rect()// for if reflection fails
    private var mSelectorPositionField: Field? = null
    private var mClippingToPadding = true
    private var mBlockLayoutChildren = false

    private val selectorPosition: Int
        get() {
            if (mSelectorPositionField == null) {
                for (i in 0 until childCount) {
                    if (getChildAt(i).bottom == mSelectorRect.bottom) {
                        return i.plus(fixedFirstVisibleItem)
                    }
                }
            } else {
                try {
                    return mSelectorPositionField?.getInt(this) ?: -1
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return -1
        }

    // first getFirstVisiblePosition() reports items
    // outside the view sometimes on old versions of android
    // work around to fix bug with firstVisibleItem being to high
    // because list view does not take clipToPadding=false into account
    // on old versions of android
    val fixedFirstVisibleItem: Int
        get() {
            return firstVisiblePosition
        }

    internal interface LifeCycleListener {
        fun onDispatchDrawOccurred(canvas: Canvas)
    }

    init {
        // Use reflection to be able to change the size/position of the list
        // selector so it does not come under/over the header
        try {
            val selectorRectField = AbsListView::class.java.getDeclaredField("mSelectorRect")
            selectorRectField.isAccessible = true
            mSelectorRect = selectorRectField.get(this) as Rect

            mSelectorPositionField =
                AbsListView::class.java.getDeclaredField("mSelectorPosition")
            mSelectorPositionField?.isAccessible = true

        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    override fun performItemClick(view: View, position: Int, id: Long): Boolean {
        var view = view
        if (view is WrapperView) {
            //view = view.item
            view.item?.let { view = it }
        }
        return super.performItemClick(view, position, id)
    }

    private fun positionSelectorRect() {
        if (!mSelectorRect.isEmpty) {
            val selectorPosition = selectorPosition
            if (selectorPosition >= 0) {
                val firstVisibleItem = fixedFirstVisibleItem
                val v = getChildAt(selectorPosition - firstVisibleItem)
                if (v is WrapperView) {
                    mSelectorRect.top = v.top + v.mItemTop
                }
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        positionSelectorRect()
        if (mTopClippingLength != 0) {
            canvas.save()
            val clipping = canvas.clipBounds
            clipping.top = mTopClippingLength
            canvas.clipRect(clipping)
            super.dispatchDraw(canvas)
            canvas.restore()
        } else {
            super.dispatchDraw(canvas)
        }
        mLifeCycleListener?.onDispatchDrawOccurred(canvas)
    }

    fun setLifeCycleListener(lifeCycleListener: LifeCycleListener) {
        mLifeCycleListener = lifeCycleListener
    }

    override fun addFooterView(v: View) {
        super.addFooterView(v)
        addInternalFooterView(v)
    }

    override fun addFooterView(v: View, data: Any, isSelectable: Boolean) {
        super.addFooterView(v, data, isSelectable)
        addInternalFooterView(v)
    }

    private fun addInternalFooterView(v: View) {
        if (mFooterViews == null) {
            mFooterViews = ArrayList()
        }
        mFooterViews?.add(v)
    }

    override fun removeFooterView(v: View): Boolean {
        if (super.removeFooterView(v)) {
            mFooterViews?.remove(v)
            return true
        }
        return false
    }

    fun containsFooterView(v: View): Boolean {
        return mFooterViews?.contains(v) ?: false
    }

    fun setTopClippingLength(topClipping: Int) {
        mTopClippingLength = topClipping
    }

    override fun setClipToPadding(clipToPadding: Boolean) {
        mClippingToPadding = clipToPadding
        super.setClipToPadding(clipToPadding)
    }

    override fun layoutChildren() {
        if (!mBlockLayoutChildren) {
            super.layoutChildren()
        }
    }
}
