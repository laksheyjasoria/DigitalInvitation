package com.app.templatestudio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TemplateStudioControllerUploadPathTest {
    @Test
    void acceptsRelativeNestedAssetPaths() {
        assertEquals("assets/fonts/display.woff2",
                TemplateStudioController.safeArchiveEntryName("assets/fonts/display.woff2"));
    }

    @Test
    void rejectsParentTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> TemplateStudioController.safeArchiveEntryName("../outside.js"));
        assertThrows(IllegalArgumentException.class,
                () -> TemplateStudioController.safeArchiveEntryName("assets/../../outside.js"));
    }

    @Test
    void rejectsAbsoluteAndWindowsPaths() {
        assertThrows(IllegalArgumentException.class,
                () -> TemplateStudioController.safeArchiveEntryName("/etc/passwd"));
        assertThrows(IllegalArgumentException.class,
                () -> TemplateStudioController.safeArchiveEntryName("C:/temp/script.js"));
        assertThrows(IllegalArgumentException.class,
                () -> TemplateStudioController.safeArchiveEntryName("assets\\script.js"));
    }

    @Test
    void rejectsEmptyAndControlCharacterPaths() {
        assertThrows(IllegalArgumentException.class,
                () -> TemplateStudioController.safeArchiveEntryName("assets//script.js"));
        assertThrows(IllegalArgumentException.class,
                () -> TemplateStudioController.safeArchiveEntryName("assets/\nscript.js"));
    }
}
