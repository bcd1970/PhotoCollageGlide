package com.photocollage.glide.ui.gallery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.photocollage.glide.data.AlbumModel
import com.photocollage.glide.databinding.ItemAlbumBinding

class AlbumAdapter(
    private val onAlbumClick: (AlbumModel) -> Unit
) : ListAdapter<AlbumModel, AlbumAdapter.AlbumViewHolder>(AlbumDiffCallback()) {
    
    // Glide options for album covers
    private val glideOptions = RequestOptions()
        .centerCrop()
        .override(300, 300)
        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
        .dontAnimate()
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val binding = ItemAlbumBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AlbumViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    override fun onViewRecycled(holder: AlbumViewHolder) {
        super.onViewRecycled(holder)
        // Clear Glide request to free memory
        Glide.with(holder.itemView.context).clear(holder.binding.albumCover)
    }
    
    inner class AlbumViewHolder(
        val binding: ItemAlbumBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onAlbumClick(getItem(position))
                }
            }
        }
        
        fun bind(album: AlbumModel) {
            // Load album cover with Glide
            album.coverUri?.let { uri ->
                Glide.with(binding.albumCover.context)
                    .load(uri)
                    .apply(glideOptions)
                    .thumbnail(0.25f)
                    .placeholder(android.R.color.darker_gray)
                    .into(binding.albumCover)
            }
            
            // Set album info
            binding.albumName.text = album.name
            binding.photoCount.text = "${album.photoCount} photos"
        }
    }
}

class AlbumDiffCallback : DiffUtil.ItemCallback<AlbumModel>() {
    override fun areItemsTheSame(oldItem: AlbumModel, newItem: AlbumModel): Boolean {
        return oldItem.id == newItem.id
    }
    
    override fun areContentsTheSame(oldItem: AlbumModel, newItem: AlbumModel): Boolean {
        return oldItem == newItem
    }
}