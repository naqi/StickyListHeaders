package se.emilsjolander.stickylistheaders

import android.annotation.SuppressLint
import android.content.Context
import android.widget.SectionIndexer

@SuppressLint("NewApi")
internal class SectionIndexerAdapterWrapper(
    context: Context,
    delegate: StickyListHeadersAdapter
) : AdapterWrapper(context, delegate), SectionIndexer {

    var mSectionIndexerDelegate: SectionIndexer = delegate as SectionIndexer

    override fun getPositionForSection(section: Int): Int {
        return mSectionIndexerDelegate.getPositionForSection(section)
    }

    override fun getSectionForPosition(position: Int): Int {
        return mSectionIndexerDelegate.getSectionForPosition(position)
    }

    override fun getSections(): Array<Any> {
        return mSectionIndexerDelegate.sections
    }

}
