package com.beacon.check.ibeaconscan.ui

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.beacon.check.ibeaconscan.R
import kotlinx.android.synthetic.main.item_list.view.*

class RecyclerAdapter : RecyclerView.Adapter<RecyclerAdapter.ItemViewHolder>() {
    override fun onBindViewHolder(p0: ItemViewHolder, p1: Int) {
        p0.itemView.tvCategoryTitle.text = dataset[p1]
    }


    val dataset: MutableList<String> = ArrayList()

    override fun onCreateViewHolder(p0: ViewGroup, p1: Int): ItemViewHolder {
        val v: View = LayoutInflater.from(p0.context).inflate(R.layout.item_list, p0, false)
        return ItemViewHolder(v)
    }

    override fun getItemCount(): Int = dataset.size


    fun updateAdapter(list: List<Any?>) {
        dataset.clear()
        list.forEach {
            dataset.add(it.toString())
        }
        notifyDataSetChanged()
    }

    open inner class ItemViewHolder(val view: View) : RecyclerView.ViewHolder(view)
}