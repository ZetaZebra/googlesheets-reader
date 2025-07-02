package com.example.googlesheets_reader;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/sheets")
public class SheetsController {

    @Autowired
    private SheetService sheetService;

    @Autowired
    private PdfExportService pdfExportService;

    @GetMapping("/table")
    public String previewSheets(@RequestParam(required = false) Integer month,
                                @RequestParam(required = false) Integer year,
                                Model model) throws Exception {

        if (month == null) month = 0;
        if (year == null) year = LocalDate.now().getYear() + 543;

        List<Map<String, String>> singleData = sheetService.getSingleTableFromRange(
                sheetService.getSingleSheetId(),
                sheetService.getSingleSheetName(),
                "A:J",
                "dd/MM/yyyy",
                month,
                year
        );

        // ✅ ใช้ raw data สำหรับ HTML preview → ไม่ filter
        List<Map<String, String>> tripleData = sheetService.getTripleTableRawData(
                sheetService.getTripleSheetId(),
                sheetService.getTripleSheetName(),
                "A:B"
        );

        // ปรับ class สีตามค่าจริง
        for (Map<String, String> row : tripleData) {
            String value = row.get("https://iams.pea.co.th/");
            if ("TRUE".equalsIgnoreCase(value)) {
                row.put("class", "true-cell");
            } else if ("FALSE".equalsIgnoreCase(value)) {
                row.put("class", "false-cell");
            } else {
                row.put("class", "");
            }
        }

        // สรุปจำนวนเครื่องหมาย '/' ในประเภทประเด็น
        Map<String, String> slashSummary = new LinkedHashMap<>();
        String[] types = {"Master Data", "Request", "Bug", "Error", "สอบถามทั่วไป"};
        int slashTotal = 0;
        for (String type : types) {
            int count = (int) singleData.stream()
                    .filter(row -> "/".equals(row.get(type)))
                    .count();
            slashSummary.put(type, String.valueOf(count));
            slashTotal += count;
        }

        model.addAttribute("singleData", singleData);
        model.addAttribute("tripleData", tripleData);
        model.addAttribute("slashSummary", slashSummary);
        model.addAttribute("slashTotal", slashTotal);
        model.addAttribute("param", Map.of("month", month, "year", year));
        model.addAttribute("defaultYear", year);

        return "sheets";
    }

    @GetMapping("/export/single")
    public void exportSingle(@RequestParam(required = false) Integer month,
                             @RequestParam(required = false) Integer year,
                             HttpServletResponse response) throws Exception {
        pdfExportService.exportSinglePdf(month, year, response);
    }

    @GetMapping("/export/triple")
    public void exportTriple(@RequestParam(required = false) Integer month,
                             @RequestParam(required = false) Integer year,
                             HttpServletResponse response) throws Exception {
        pdfExportService.exportTriplePdf(month, year, response);
    }
}