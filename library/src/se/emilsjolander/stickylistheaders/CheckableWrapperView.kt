package se.emilsjolander.stickylistheaders

import android.content.Context
import android.widget.Checkable

/**
 * A WrapperView that implements the checkable interface
 *
 * @author Emil Sjölander
 * @modified RNies 2019
 */
internal class CheckableWrapperView(context: Context) : WrapperView(context), Checkable {

    override fun isChecked(): Boolean {
        return (item as Checkable).isChecked
    }

    override fun setChecked(checked: Boolean) {
        (item as Checkable).isChecked = checked
    }

    override fun toggle() {
        isChecked = !isChecked
    }
}
