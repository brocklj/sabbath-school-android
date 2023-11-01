/*
 * Copyright (c) 2023. Adventech <info@adventech.io>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package app.ss.pdf.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ss.lessons.data.repository.media.MediaRepository
import app.ss.lessons.data.repository.user.UserDataRepository
import app.ss.models.LessonPdf
import app.ss.models.PdfAnnotations
import app.ss.models.media.MediaAvailability
import com.cryart.sabbathschool.core.extensions.intent.lessonIndex
import com.pspdfkit.annotations.Annotation
import com.pspdfkit.document.PdfDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ss.lessons.api.PdfReader
import ss.lessons.model.LocalFile
import javax.inject.Inject

@HiltViewModel
class ReadPdfViewModel @Inject constructor(
    private val pdfReader: PdfReader,
    private val userDataRepository: UserDataRepository,
    private val mediaRepository: MediaRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _pdfFiles = MutableStateFlow<List<LocalFile>>(emptyList())
    val pdfsFilesFlow: StateFlow<List<LocalFile>> = _pdfFiles.asStateFlow()

    private val SavedStateHandle.pdfs: List<LessonPdf>
        get() = get<ArrayList<LessonPdf>>(ARG_PDF_FILES) ?: emptyList()

    val lessonIndex: String? get() = savedStateHandle.lessonIndex

    private val _annotationsUpdate = MutableSharedFlow<Int>()
    val annotationsUpdateFlow: SharedFlow<Int> = _annotationsUpdate

    private val _annotationsMap: MutableMap<Int, List<PdfAnnotations>> = mutableMapOf()
    val annotationsMap: Map<Int, List<PdfAnnotations>> = _annotationsMap

    private val mediaAvailability = MutableStateFlow(MediaAvailability())
    val mediaAvailabilityFlow = mediaAvailability.asStateFlow()

    init {
        viewModelScope.launch {
            val lessonIndex = lessonIndex ?: return@launch
            checkMediaAvailability(lessonIndex)
            savedStateHandle.pdfs.forEachIndexed { index, pdf ->
                userDataRepository.getAnnotations(lessonIndex, pdf.id).collect { result ->
                    val syncAnnotations = result.getOrNull() ?: return@collect
                    _annotationsMap[index] = syncAnnotations
                    _annotationsUpdate.emit(index)
                }
            }
        }

        downloadFiles()
    }

    private fun checkMediaAvailability(lessonIndex: String) {
        viewModelScope.launch {
            val audioAvailable = mediaRepository.getAudio(lessonIndex).data.isNullOrEmpty().not()
            val videoAvailable = mediaRepository.getVideo(lessonIndex).data.isNullOrEmpty().not()

            mediaAvailability.update { MediaAvailability(audioAvailable, videoAvailable) }
        }
    }

    private fun downloadFiles() = viewModelScope.launch {
        val result = pdfReader.downloadFiles(savedStateHandle.pdfs)
        val files = result.getOrDefault(emptyList())
        _pdfFiles.update { files }
    }

    fun saveAnnotations(document: PdfDocument, docIndex: Int) {
        val lessonIndex = lessonIndex ?: return
        val pdfId = savedStateHandle.pdfs.getOrNull(docIndex)?.id ?: return

        val syncAnnotations = document.annotations().toSync()

        userDataRepository.saveAnnotations(lessonIndex, pdfId, syncAnnotations)
    }

    private fun List<Annotation>.toSync(): List<PdfAnnotations> {
        val groupedAnnotations = groupBy { it.pageIndex }
        return groupedAnnotations.keys.mapNotNull { pageIndex ->
            val list = groupedAnnotations[pageIndex] ?: return@mapNotNull null
            val annotations = list.map { it.toInstantJson() }.filter(::invalidInstantJson)
            PdfAnnotations(pageIndex, annotations)
        }
    }

    private fun invalidInstantJson(json: String) = json != "null"
}

fun PdfDocument.annotations(): List<Annotation> {
    val allAnnotations = mutableListOf<Annotation>()
    for (i in 0 until pageCount) {
        val annotations = annotationProvider.getAnnotations(i)
        allAnnotations.addAll(annotations)
    }
    return allAnnotations
}
