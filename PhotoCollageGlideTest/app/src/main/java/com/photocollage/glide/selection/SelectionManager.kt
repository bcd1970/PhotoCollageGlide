package com.photocollage.glide.selection

import com.photocollage.glide.data.PhotoModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SelectionManager private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: SelectionManager? = null
        
        fun getInstance(): SelectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SelectionManager().also { INSTANCE = it }
            }
        }
    }
    
    enum class SelectionMode {
        NONE,
        EDIT,
        COLLAGE
    }
    
    private val _selectedPhotos = MutableStateFlow<Set<Long>>(emptySet())
    val selectedPhotos: StateFlow<Set<Long>> = _selectedPhotos.asStateFlow()
    
    private val _selectionMode = MutableStateFlow(SelectionMode.NONE)
    val selectionMode: StateFlow<SelectionMode> = _selectionMode.asStateFlow()
    
    private val _selectedPhotoModels = mutableMapOf<Long, PhotoModel>()
    
    fun togglePhotoSelection(photo: PhotoModel) {
        val currentSelection = _selectedPhotos.value.toMutableSet()
        
        if (currentSelection.contains(photo.id)) {
            currentSelection.remove(photo.id)
            _selectedPhotoModels.remove(photo.id)
        } else {
            currentSelection.add(photo.id)
            _selectedPhotoModels[photo.id] = photo
        }
        
        _selectedPhotos.value = currentSelection
        updateSelectionMode()
    }
    
    fun isPhotoSelected(photoId: Long): Boolean {
        return _selectedPhotos.value.contains(photoId)
    }
    
    fun getSelectedPhotoModels(): List<PhotoModel> {
        return _selectedPhotos.value.mapNotNull { id ->
            _selectedPhotoModels[id]
        }
    }
    
    fun clearSelection() {
        _selectedPhotos.value = emptySet()
        _selectedPhotoModels.clear()
        _selectionMode.value = SelectionMode.NONE
    }
    
    fun removePhotoFromSelection(photoId: Long) {
        val currentSelection = _selectedPhotos.value.toMutableSet()
        currentSelection.remove(photoId)
        _selectedPhotoModels.remove(photoId)
        _selectedPhotos.value = currentSelection
        updateSelectionMode()
    }
    
    private fun updateSelectionMode() {
        _selectionMode.value = when (_selectedPhotos.value.size) {
            0 -> SelectionMode.NONE
            1 -> SelectionMode.EDIT
            else -> SelectionMode.COLLAGE
        }
    }
    
    fun getSelectionCount(): Int = _selectedPhotos.value.size
    
    fun hasSelection(): Boolean = _selectedPhotos.value.isNotEmpty()
}