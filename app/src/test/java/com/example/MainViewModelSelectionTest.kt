package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.filemanager.model.ClipboardAction
import com.example.filemanager.utils.FileUtils
import com.example.filemanager.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainViewModelSelectionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val app = ApplicationProvider.getApplicationContext<Application>()
        viewModel = MainViewModel(app)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun awaitIdle(condition: () -> Boolean) {
        withTimeout(5_000L) {
            while (!condition()) {
                delay(20L)
            }
        }
    }

    @Test
    fun selectionMode_persistsAcrossDirectories_andBatchMovesAndDeletes() = runBlocking {
        val dirA = tempFolder.newFolder("DirA")
        val dirB = tempFolder.newFolder("DirB")
        val targetDir = tempFolder.newFolder("Target")

        val fileA1 = File(dirA, "a1.txt").apply { writeText("A1") }
        val fileA2 = File(dirA, "a2.txt").apply { writeText("A2") }
        val fileB1 = File(dirB, "b1.txt").apply { writeText("B1") }

        viewModel.enterSelectionMode()
        assertTrue(viewModel.isMultiSelectMode.value)

        viewModel.loadDirectory(dirA, preserveSelection = true)
        awaitIdle { !viewModel.isLoading.value }
        viewModel.toggleSelectItem(FileUtils.fileToFileItem(fileA1))

        // Navigating to dirB while in selection mode MUST retain selection from dirA
        viewModel.enterDirectory(dirB)
        awaitIdle { !viewModel.isLoading.value }
        assertTrue(viewModel.isMultiSelectMode.value)
        assertEquals(1, viewModel.selectedItems.value.size)

        viewModel.toggleSelectItem(FileUtils.fileToFileItem(fileB1))
        assertEquals(2, viewModel.selectedItems.value.size)

        // Invert selection in dirB retains fileA1 from dirA while toggling fileB1 off
        viewModel.invertSelectionInDirectory(listOf(FileUtils.fileToFileItem(fileB1)))
        assertEquals(1, viewModel.selectedItems.value.size)
        assertEquals(fileA1.absolutePath, viewModel.selectedItems.value.first().path)

        // Re-select fileB1 and batch move both across directories into Target
        viewModel.toggleSelectItem(FileUtils.fileToFileItem(fileB1))
        assertEquals(2, viewModel.selectedItems.value.size)

        viewModel.batchMoveOrCopySelected(targetDir = targetDir, isMove = true)
        awaitIdle { File(targetDir, "b1.txt").exists() && !viewModel.operationProgress.value.isRunning }

        assertFalse(fileA1.exists())
        assertFalse(fileB1.exists())
        assertTrue(File(targetDir, "a1.txt").exists())
        assertTrue(File(targetDir, "b1.txt").exists())
        assertTrue(fileA2.exists())
        assertFalse(viewModel.isMultiSelectMode.value)
        assertTrue(viewModel.selectedItems.value.isEmpty())

        // Verify batch delete across directories works cleanly
        viewModel.enterSelectionMode()
        viewModel.toggleSelectItem(FileUtils.fileToFileItem(File(targetDir, "a1.txt")))
        viewModel.toggleSelectItem(FileUtils.fileToFileItem(fileA2))
        viewModel.deleteSelectedItems()
        awaitIdle { !File(targetDir, "a1.txt").exists() && !fileA2.exists() && !viewModel.operationProgress.value.isRunning }

        assertFalse(File(targetDir, "a1.txt").exists())
        assertFalse(fileA2.exists())
        assertTrue(File(targetDir, "b1.txt").exists())
        assertTrue(viewModel.selectedItems.value.isEmpty())
    }

    @Test
    fun clipboardAndSingleItemMoveOrCopy_workCorrectly() = runBlocking {
        val sourceDir = tempFolder.newFolder("SourceDir")
        val targetDir = tempFolder.newFolder("TargetDir")
        val nestedDir = File(sourceDir, "Nested").apply { mkdirs() }

        val sampleFile = File(sourceDir, "note.txt").apply { writeText("Hello") }
        val sampleItem = FileUtils.fileToFileItem(sampleFile)
        val nestedItem = FileUtils.fileToFileItem(nestedDir)

        // Copy via clipboard into targetDir
        viewModel.setClipboardItems(ClipboardAction.COPY, listOf(sampleItem))
        assertEquals(ClipboardAction.COPY, viewModel.clipboard.value?.action)
        viewModel.pasteClipboard(targetDir)
        awaitIdle { File(targetDir, "note.txt").exists() && !viewModel.operationProgress.value.isRunning }

        assertTrue(sampleFile.exists())
        assertTrue(File(targetDir, "note.txt").exists())
        // Copy keeps clipboard until cleared or replaced
        assertEquals(1, viewModel.clipboard.value?.items?.size)

        // Cut via clipboard and verify same-directory paste is safely ignored (file stays intact)
        viewModel.setClipboardItems(ClipboardAction.CUT, listOf(sampleItem))
        viewModel.pasteClipboard(sourceDir)
        assertTrue(sampleFile.exists())

        // Paste cut into nestedDir moves file and clears clipboard
        viewModel.pasteClipboard(nestedDir)
        awaitIdle { File(nestedDir, "note.txt").exists() && !viewModel.operationProgress.value.isRunning }
        assertFalse(sampleFile.exists())
        assertTrue(File(nestedDir, "note.txt").exists())
        assertNull(viewModel.clipboard.value)

        // Direct single-item copy of folder
        viewModel.moveOrCopySingleItem(nestedItem, targetDir, isMove = false)
        awaitIdle { File(targetDir, "Nested/note.txt").exists() && !viewModel.operationProgress.value.isRunning }
        assertTrue(File(nestedDir, "note.txt").exists())
        assertTrue(File(targetDir, "Nested/note.txt").exists())
    }
}
