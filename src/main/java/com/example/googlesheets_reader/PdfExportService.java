package com.example.googlesheets_reader;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.PdfWriter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

@Service
public class PdfExportService {

    @Autowired
    private SheetService sheetService;

    @Autowired
    private PdfUtil pdfUtil;

    public void exportSinglePdf(Integer month, Integer year, HttpServletResponse response) throws Exception {
        List<Map<String, String>> filtered = sheetService.getSingleTableFromRange(
                sheetService.getSingleSheetId(),
                sheetService.getSingleSheetName(),
                "A:J",
                "dd/MM/yyyy",
                month,
                year
        );

        String[] headers = {
                "ลำดับ", "หัวข้อ", "รายละเอียด", "วันที่แจ้ง", "สถานะ",
                "Master Data", "Request", "Bug", "Error", "สอบถามทั่วไป"
        };

        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of(headers));

        for (Map<String, String> row : filtered) {
            List<String> line = new ArrayList<>();
            for (String h : headers) {
                line.add(row.getOrDefault(h, ""));
            }
            rows.add(line);
        }

        // แถวสรุป
        List<String> summaryRow = new ArrayList<>(Collections.nCopies(headers.length, ""));
        summaryRow.set(0, "รวมตามประเภท");
        for (int i = 5; i < headers.length; i++) {
            final String key = headers[i];
            int count = (int) filtered.stream()
                    .filter(row -> "/".equals(row.get(key)))
                    .count();
            summaryRow.set(i, String.valueOf(count));
        }

        List<String> totalRow = new ArrayList<>(Collections.nCopies(headers.length, ""));
        totalRow.set(0, "รวมทั้งหมด");
        int totalSlash = summaryRow.subList(5, headers.length).stream()
                .mapToInt(s -> Integer.parseInt(s)).sum();
        totalRow.set(5, String.valueOf(totalSlash)); // แสดงตรงกลางของคอลัมน์ 5 ช่องท้าย

        rows.add(summaryRow);
        rows.add(totalRow);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=single-table.pdf");
        pdfUtil.exportSingleTable(rows, response.getOutputStream());
    }

    public void exportTriplePdf(Integer month, Integer year, HttpServletResponse response) throws Exception {
        List<Map<String, String>> allRows = sheetService.getTripleTableFromRange(
                sheetService.getTripleSheetId(),
                sheetService.getTripleSheetName(),
                "A:B",
                "yyyy-MM-dd HH:mm:ss",
                month,
                year
        );

        System.out.println("จำนวน rows ก่อน filter = " + allRows.size());

        List<Map<String, String>> filteredRows = sheetService.filterByTimeInterval(
                allRows,
                "DATE_TIME", //ชื่อคอลัมน์จริง
                "yyyy-MM-dd HH:mm:ss",
                20 //แถวถัดไป ต้องห่างกันไม่น้อยกว่า 20 นาที จึงจะนำมาแสดงใน pdf เพื่อลดหน้ากระดาษ
        );

        if (filteredRows.isEmpty()) {
            System.out.println("ข้อมูลหลังกรองว่าง → ไม่สร้าง PDF");
            return;
        }

        List<List<String>> rows = convertMapToRows(filteredRows);

        int rowsPerPage = 75;

        List<List<List<List<String>>>> pages =
                sheetService.splitTripleTableDataLeftToRight(rows, rowsPerPage);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=triple-table.pdf");

        Document document = new Document(PageSize.A4.rotate(), 36, 36, 20, 36);
        PdfWriter.getInstance(document, response.getOutputStream());
        document.open();

        for (List<List<List<String>>> page : pages) {
            List<List<String>> left = page.get(0);
            List<List<String>> center = page.get(1);
            List<List<String>> right = page.get(2);

            pdfUtil.export3VerticalTables(left, center, right, document);
            document.newPage();
        }

        document.close();
    }

    private List<List<String>> convertMapToRows(List<Map<String, String>> data) {
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, String> map : data) {
            rows.add(new ArrayList<>(map.values()));
        }
        return rows;
    }
}