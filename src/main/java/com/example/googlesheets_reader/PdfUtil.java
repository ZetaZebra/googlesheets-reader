package com.example.googlesheets_reader;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class PdfUtil {

    private Font getThaiFont() throws DocumentException, IOException {
        String fontPath = "fonts/THSarabunNew.ttf";
        BaseFont baseFont = BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        return new Font(baseFont, 14);
    }

    private Font getBoldFont() throws DocumentException, IOException {
        String fontPath = "fonts/THSarabunNew.ttf";
        BaseFont baseFont = BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        return new Font(baseFont, 16, Font.BOLD);
    }

    public void exportSingleTable(List<List<String>> rows, OutputStream out) throws DocumentException, IOException {
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 20, 36);
        PdfWriter.getInstance(document, out);
        document.open();

        Font thaiFont = getThaiFont();
        Font boldFont = getBoldFont();

        Paragraph attachment = new Paragraph("เอกสารแนบ 1", boldFont);
        attachment.setAlignment(Element.ALIGN_RIGHT);
        attachment.setSpacingAfter(5f);
        document.add(attachment);

        PdfPTable table = new PdfPTable(rows.get(0).size());
        table.setWidthPercentage(100);
        table.setHeaderRows(1);

        float[] columnWidths = {2f, 6f, 18f, 4f, 3f, 3f, 3f, 3f, 3f, 3f};
        table.setWidths(columnWidths);

        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            boolean isSummaryRow = row.get(0).equals("รวมตามประเภท") || row.get(0).equals("รวมทั้งหมด");

            for (int i = 0; i < row.size(); i++) {
                String cell = row.get(i);

                // แถวหัวตาราง
                if (r == 0) {
                    PdfPCell header = new PdfPCell(new Phrase(cell, thaiFont));
                    header.setBackgroundColor(BaseColor.LIGHT_GRAY);
                    header.setHorizontalAlignment(Element.ALIGN_CENTER);
                    table.addCell(header);
                }
                // แถวสรุป: รวมข้อความ 5 ช่องแรก
                else if (isSummaryRow && i == 0) {
                    PdfPCell summaryCell = new PdfPCell(new Phrase(cell, thaiFont));
                    summaryCell.setColspan(5);
                    summaryCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    table.addCell(summaryCell);
                    i += 4;
                }
                // แถวรวมทั้งหมด: รวมค่ารวมในช่องกลาง
                else if (row.get(0).equals("รวมทั้งหมด") && i == 5) {
                    PdfPCell totalCell = new PdfPCell(new Phrase(cell, thaiFont));
                    totalCell.setColspan(5);
                    totalCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    table.addCell(totalCell);
                    i += 4;
                }
                else if (!(isSummaryRow && (i > 0 && i <= 4)) && !(row.get(0).equals("รวมทั้งหมด") && (i > 5 && i <= 9))) {
                    Font cellFont = cell.equals("/") ? boldFont : thaiFont;
                    PdfPCell dataCell = new PdfPCell(new Phrase(cell, cellFont));

                    // กำหนดตำแหน่งเฉพาะบางคอลัมน์
                    if (cell.equals("/")) {
                        dataCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    } else if ((r > 0 && i == 3) || i == 4) {
                        dataCell.setHorizontalAlignment(Element.ALIGN_CENTER); // วันที่แจ้ง/สถานะ
                    } else if (row.get(0).equals("รวมตามประเภท") && i >= 5) {
                        dataCell.setHorizontalAlignment(Element.ALIGN_CENTER); // ตัวเลขผลรวม
                    }

                    table.addCell(dataCell);
                }
            }
        }

        document.add(table);
        document.close();
    }
    public void export3VerticalTables(
            List<List<String>> left,
            List<List<String>> center,
            List<List<String>> right,
            Document document
    ) throws DocumentException, IOException {

        Font thaiFont = getThaiFont();
        Font boldFont = getBoldFont();

        Paragraph attachment = new Paragraph("เอกสารแนบ 2", boldFont);
        attachment.setAlignment(Element.ALIGN_RIGHT);
        attachment.setSpacingAfter(10f);
        document.add(attachment);

        PdfPTable tableLeft = createTableWithHeader(thaiFont);
        PdfPTable tableCenter = createTableWithHeader(thaiFont);
        PdfPTable tableRight = createTableWithHeader(thaiFont);

        addTableData(tableLeft, left, thaiFont);
        addTableData(tableCenter, center, thaiFont);
        addTableData(tableRight, right, thaiFont);

        // เช็คว่ามี table ไหนมีข้อมูล
        List<PdfPTable> tablesToAdd = new java.util.ArrayList<>();
        if (tableLeft.size() > 1) { // > 1 เพราะ header 1 แถว
            tablesToAdd.add(tableLeft);
        }
        if (tableCenter.size() > 1) {
            tablesToAdd.add(tableCenter);
        }
        if (tableRight.size() > 1) {
            tablesToAdd.add(tableRight);
        }

        if (!tablesToAdd.isEmpty()) {
            if (tablesToAdd.size() == 1) {
                // กรณีเหลือ 1 ตาราง → จัดให้ครึ่งหน้า
                PdfPTable outerTable = new PdfPTable(new float[]{1f, 1f});
                outerTable.setWidthPercentage(100);
                outerTable.addCell(wrapTableInCell(tablesToAdd.get(0)));

                // Cell เปล่าอีกฝั่ง
                PdfPCell emptyCell = new PdfPCell(new Phrase(""));
                emptyCell.setBorder(Rectangle.NO_BORDER);
                outerTable.addCell(emptyCell);

                document.add(outerTable);
            } else {
                float[] widths = new float[tablesToAdd.size()];
                Arrays.fill(widths, 1f);

                PdfPTable outerTable = new PdfPTable(widths);
                outerTable.setWidthPercentage(100);

                for (PdfPTable tbl : tablesToAdd) {
                    outerTable.addCell(wrapTableInCell(tbl));
                }

                document.add(outerTable);
            }
        }
    }

    private PdfPTable createTableWithHeader(Font thaiFont) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);

        PdfPCell header1 = new PdfPCell(new Phrase("DATE-TIME", thaiFont));
        header1.setBackgroundColor(BaseColor.LIGHT_GRAY);
        header1.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(header1);

        PdfPCell header2 = new PdfPCell(new Phrase("IP URL IAMS SERVER\nhttp://172.16.100.141", thaiFont));
        header2.setBackgroundColor(BaseColor.LIGHT_GRAY);
        header2.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(header2);

        return table;
    }

    private void addTableData(PdfPTable table, List<List<String>> data, Font thaiFont) {
        for (List<String> row : data) {
            for (String text : row) {
                PdfPCell cell = new PdfPCell(new Phrase(text, thaiFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);

                if ("TRUE".equalsIgnoreCase(text)) {
                    cell.setBackgroundColor(BaseColor.GREEN);
                } else if ("FALSE".equalsIgnoreCase(text)) {
                    cell.setBackgroundColor(BaseColor.RED);
                }

                table.addCell(cell);
            }
        }
    }

    private PdfPCell wrapTableInCell(PdfPTable table) {
        PdfPCell cell = new PdfPCell(table);
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }


    private PdfPCell getCell(List<List<String>> data, int index, Font font) {
        if (index >= data.size()) return new PdfPCell(new Phrase(""));
        List<String> row = data.get(index);
        String content = (row.size() >= 2) ? row.get(0) + "  " + row.get(1) : "";
        return new PdfPCell(new Phrase(content, font));
    }
}