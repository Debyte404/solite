package solite.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** Icon mapping, size formatting, and cell-accurate width maths. */
class IconsAndFormatTest {

    private static String icon(String name) {
        return SimpleUI.iconFor(Paths.get("C:/somewhere", name));
    }

    @ParameterizedTest
    @CsvSource({
            "photo.jpg, IMG",
            "photo.JPEG, IMG",
            "shot.png, IMG",
            "anim.gif, IMG",
            "vector.svg, IMG",
            "bundle.zip, ZIP",
            "backup.7z, ZIP",
            "disk.iso, ZIP",
            "song.mp3, AUDIO",
            "track.flac, AUDIO",
            "clip.mp4, VIDEO",
            "movie.mkv, VIDEO",
            "setup.exe, APP",
            "installer.msi, APP",
            "Main.java, CODE",
            "app.py, CODE",
            "index.html, CODE",
            "notes.txt, CODE",
            "report.pdf, DOC",
            "contract.docx, DOC"
    })
    void iconMatchesTheFileType(String file, String expected) {
        String actual = icon(file)
                .equals(SimpleUI.IMG_ICON) ? "IMG"
                : icon(file).equals(SimpleUI.ZIP_ICON) ? "ZIP"
                : icon(file).equals(SimpleUI.AUDIO_ICON) ? "AUDIO"
                : icon(file).equals(SimpleUI.VIDEO_ICON) ? "VIDEO"
                : icon(file).equals(SimpleUI.APP_ICON) ? "APP"
                : icon(file).equals(SimpleUI.CODE_ICON) ? "CODE" : "DOC";
        assertEquals(expected, actual, "icon for " + file);
    }

    @Test
    void extensionMatchingIgnoresCase() {
        assertEquals(SimpleUI.IMG_ICON, icon("HOLIDAY.JPG"));
        assertEquals(SimpleUI.ZIP_ICON, icon("BUNDLE.ZIP"));
    }

    @Test
    void filesWithoutAnExtensionGetTheDocumentIcon() {
        assertEquals(SimpleUI.DOC_ICON, icon("LICENSE"));
    }

    @Test
    void dotfilesAreTreatedAsHavingNoExtension() {
        assertEquals(SimpleUI.DOC_ICON, icon(".gitignore"));
    }

    @Test
    void folderPathGetsTheFolderIcon(@org.junit.jupiter.api.io.TempDir Path dir) {
        assertEquals(SimpleUI.BACK_ICON, SimpleUI.iconFor(dir));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0 B",
            "1, 1 B",
            "1023, 1023 B",
            "1024, 1.0 KB",
            "2048, 2.0 KB",
            "1048576, 1 MB",
            "5242880, 5 MB",
            "1073741824, 1.0 GB"
    })
    void formatSizeReadsNaturally(long bytes, String expected) {
        assertEquals(expected, SimpleUI.formatSize(bytes));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "ab", "abcde", "hello world", "notes.txt"})
    void plainTextIsOneCellPerCharacter(String s) {
        assertEquals(s.length(), SimpleUI.displayWidth(s));
    }

    @Test
    void emojiAreTwoCells() {
        assertEquals(2, SimpleUI.displayWidth(SimpleUI.BACK_ICON));
        assertEquals(4, SimpleUI.displayWidth(SimpleUI.BACK_ICON + SimpleUI.ZIP_ICON));
        assertEquals(5, SimpleUI.displayWidth(SimpleUI.BACK_ICON + " ab"));
    }

    @Test
    void truncateKeepsTheStringWhenItFits() {
        assertEquals("abc", SimpleUI.truncate("abc", 10));
    }

    @Test
    void truncateCutsToTheWidth() {
        assertEquals("abc", SimpleUI.truncate("abcdef", 3));
    }

    @Test
    void truncateNeverSplitsAnEmoji() {
        String s = SimpleUI.BACK_ICON + SimpleUI.BACK_ICON + "xyz";
        String cut = SimpleUI.truncate(s, 3);
        assertEquals(2, SimpleUI.displayWidth(cut), "one whole emoji, never half of one");
    }

    @Test
    void truncateAtZeroIsEmpty() {
        assertEquals("", SimpleUI.truncate("anything", 0));
        assertEquals("", SimpleUI.truncate("anything", -5));
    }

    @Test
    void padFillsToTheWidth() {
        assertEquals("ab   ", SimpleUI.pad("ab", 5));
    }

    @Test
    void padNeverShrinksLongerText() {
        assertEquals("abcdef", SimpleUI.pad("abcdef", 3));
    }
}
