package com.booking.system.hr;

import com.booking.system.hr.service.HrDocxEngine;
import com.booking.system.hr.service.HrDocumentTemplateService;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;

class HrDocxEngineTest {
    @Test void fillsSplitRunsAndRepeatedTokensWithoutLosingSurroundingText() throws Exception {
        byte[] input = docx("<w:r><w:t>Trước {{FULL_</w:t></w:r><w:r><w:t>NAME}} sau {{AGE}} / {{AGE}}</w:t></w:r>");
        assertThat(HrDocxEngine.tokens(input)).containsExactly("{{AGE}}", "{{FULL_NAME}}");
        byte[] output = HrDocxEngine.fill(input, Map.of("{{FULL_NAME}}", "An & Bình", "{{AGE}}", "30"));
        assertThat(xml(output)).contains("Trước An &amp; Bình", " sau 30 / 30");
        assertThat(HrDocxEngine.tokens(output)).isEmpty();
    }
    @Test void allBuiltInTemplatesCanBeFilled() {
        for (String kind : List.of("OFFICE", "GENERAL_LABOR", "PROBATION")) {
            byte[] input = HrDocumentTemplateService.builtIn(kind).bytes();
            var values = new HashMap<String,String>();
            HrDocxEngine.tokens(input).forEach(t -> values.put(t, "Kiểm thử"));
            assertThat(values).isNotEmpty();
            assertThat(HrDocxEngine.tokens(HrDocxEngine.fill(input,values))).isEmpty();
        }
    }
    @Test void rejectsUnknownVariablesAndNonWordFiles() throws Exception {
        assertThatThrownBy(() -> HrDocxEngine.tokens(new byte[]{1,2,3})).hasMessageContaining(".docx");
        assertThatThrownBy(() -> HrDocxEngine.fill(docx("<w:r><w:t>{{UNKNOWN}}</w:t></w:r>"), Map.of()))
                .hasMessageContaining("chưa được hỗ trợ");
    }
    private byte[] docx(String runs) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml")); zip.write("<Types/>".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p>"+runs+"</w:p></w:body></w:document>").getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        return bytes.toByteArray();
    }
    private String xml(byte[] bytes) throws Exception {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry; while ((entry=zip.getNextEntry())!=null) if (entry.getName().equals("word/document.xml")) return new String(zip.readAllBytes(),StandardCharsets.UTF_8);
        }
        throw new AssertionError("Missing Word XML");
    }
}
