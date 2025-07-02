package com.example.googlesheets_reader;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SheetService {

    private Sheets sheetsService;

    @Value("${google.sheet.single.id}")
    private String singleSheetId;

    @Value("${google.sheet.triple.id}")
    private String tripleSheetId;

    @Value("${google.sheet.single.name}")
    private String singleSheetName;

    @Value("${google.sheet.triple.name}")
    private String tripleSheetName;

    @PostConstruct
    public void init() throws GeneralSecurityException, IOException {
        InputStream in = getClass().getResourceAsStream("/credentials.json");
        if (in == null) throw new RuntimeException("Cannot find credentials.json");

        GoogleCredentials credentials = GoogleCredentials.fromStream(in)
                .createScoped(Collections.singletonList("https://www.googleapis.com/auth/spreadsheets.readonly"));

        sheetsService = new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JacksonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)
        ).setApplicationName("Google Sheets Reader").build();
    }

    public String getSingleSheetId() {
        return singleSheetId;
    }

    public String getTripleSheetId() {
        return tripleSheetId;
    }

    public String getSingleSheetName() {
        return singleSheetName;
    }

    public String getTripleSheetName() {
        return tripleSheetName;
    }

    public List<Map<String, String>> getSingleTableFromRange(
            String sheetId,
            String sheetName,
            String range,
            String dateFormat,
            Integer month,
            Integer year
    ) throws IOException {

        List<List<Object>> values = fetchSheetData(sheetId, sheetName, range);

        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 1; i < values.size(); i++) {
            List<Object> row = values.get(i);
            Map<String, String> map = new LinkedHashMap<>();

            // Map ค่าจากคอลัมน์ที่ต้องการ
            String[] headers = {"ลำดับ", "หัวข้อ", "รายละเอียด", "วันที่แจ้ง", "สถานะ", "ประเภทประเด็น"};
            int[] indices = {0, 1, 2, 3, 6, 9}; // A, B, C, D, G, J

            for (int j = 0; j < headers.length; j++) {
                String val = (indices[j] < row.size()) ? row.get(indices[j]).toString() : "";
                map.put(headers[j], val);
            }

            // แยกประเภทประเด็นเป็น 5 คอลัมน์
            String issueType = map.get("ประเภทประเด็น");
            map.remove("ประเภทประเด็น"); // เอาออกก่อน
            String[] types = {"Master Data", "Request", "Bug", "Error", "สอบถามทั่วไป"};
            for (String type : types) {
                map.put(type, type.equalsIgnoreCase(issueType) ? "/" : "");
            }

            // กรองตามวันที่
            if (filterByDate(map, "วันที่แจ้ง", dateFormat, month, year)) {
                result.add(map);
            }
        }

        return result;
    }

    public List<Map<String, String>> getTripleTableFromRange(
            String sheetId,
            String sheetName,
            String range,
            String dateFormat,
            Integer month,
            Integer year
    ) throws IOException {

        List<List<Object>> values = fetchSheetData(sheetId, sheetName, range);
        if (values == null || values.isEmpty()) {
            System.out.println("ไม่มีข้อมูลใน Google Sheet");
            return new ArrayList<>();
        }

        // เอา header จริงจากแถวแรก
        List<String> headers = new ArrayList<>();
        for (Object cell : values.get(0)) {
            headers.add(cell != null ? cell.toString().trim() : "");
        }
        System.out.println("Header ที่ดึงมาได้:");
        System.out.println(headers);

        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < values.size(); i++) {
            List<Object> row = values.get(i);
            Map<String, String> rowMap = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                String key = headers.get(j);
                String value = (j < row.size()) ? row.get(j).toString().trim() : "";
                rowMap.put(key, value);
            }

            // ถ้ามีเงื่อนไข filter ตามวันที่
            if (filterByDate(rowMap, "DATE_TIME", dateFormat, month, year)) {
                rows.add(rowMap);
            }
        }

        System.out.println("โหลดข้อมูลสำเร็จ จำนวน rows = " + rows.size());
        return rows;
    }

    private List<List<Object>> fetchSheetData(String sheetId, String sheetName, String range) throws IOException {
        String fullRange = sheetName + "!" + range;
        ValueRange response = sheetsService.spreadsheets().values()
                .get(sheetId, fullRange)
                .execute();
        return response.getValues();
    }

    private List<Map<String, String>> processData(List<List<Object>> values, List<String> headers, String dateKey, String dateFormat, Integer month, Integer year) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (values == null || values.isEmpty()) return rows;

        for (int i = 1; i < values.size(); i++) {
            List<Object> row = values.get(i);
            Map<String, String> map = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                String key = headers.get(j);
                String value = (j < row.size()) ? row.get(j).toString() : "";
                map.put(key, value);
            }
            if (filterByDate(map, dateKey, dateFormat, month, year)) {
                rows.add(map);
            }
        }
        return rows;
    }

    private boolean filterByDate(Map<String, String> row, String dateKey, String format, Integer month, Integer year) {
        if (!row.containsKey(dateKey)) return false;

        String rawDate = row.get(dateKey);
        try {
            Date date = new SimpleDateFormat(format).parse(rawDate);
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);

            int m = cal.get(Calendar.MONTH) + 1;
            int y = cal.get(Calendar.YEAR);

            return (month == null || month == 0 || m == month)
                    && (year == null || y == (year - 543));
        } catch (ParseException e) {
            return false;
        }
    }

    public List<List<Map<String, String>>> splitDataIntoThreeParts(List<Map<String, String>> allData) {
        int size = allData.size();
        int chunk = (int) Math.ceil(size / 3.0);
        List<List<Map<String, String>>> result = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int from = i * chunk;
            int to = Math.min(from + chunk, size);
            result.add(allData.subList(from, to));
        }
        return result;
    }

    public List<List<String>> getTripleSheetData() throws IOException {
        List<List<Object>> values = fetchSheetData(tripleSheetId, tripleSheetName, "A:B");

        List<List<String>> rows = new ArrayList<>();
        for (int i = 1; i < values.size(); i++) {
            List<Object> row = values.get(i);
            String colA = row.size() > 0 ? row.get(0).toString() : "";
            String colB = row.size() > 1 ? row.get(1).toString() : "";
            rows.add(Arrays.asList(colA, colB));
        }
        return rows;
    }

    public List<List<String>> getSingleSheetData() throws IOException {
        List<List<Object>> values = fetchSheetData(singleSheetId, singleSheetName, "A:J");

        List<List<String>> rows = new ArrayList<>();
        for (int i = 1; i < values.size(); i++) {
            List<Object> row = values.get(i);
            List<String> stringRow = new ArrayList<>();
            for (Object cell : row) {
                stringRow.add(cell.toString());
            }
            rows.add(stringRow);
        }
        return rows;
    }
    public List<List<List<List<String>>>> splitTripleTableDataLeftToRight(List<List<String>> allRows, int rowsPerPage) {
        List<List<List<List<String>>>> pages = new ArrayList<>();
        int totalRows = allRows.size();

        for (int start = 0; start < totalRows; start += rowsPerPage) {
            int end = Math.min(start + rowsPerPage, totalRows);
            List<List<String>> pageRows = allRows.subList(start, end);

            List<List<String>> left = new ArrayList<>();
            List<List<String>> center = new ArrayList<>();
            List<List<String>> right = new ArrayList<>();

            int chunkSize = rowsPerPage / 3;
            for (int i = 0; i < pageRows.size(); i++) {
                if (i < chunkSize) {
                    left.add(pageRows.get(i));
                } else if (i < chunkSize * 2) {
                    center.add(pageRows.get(i));
                } else {
                    right.add(pageRows.get(i));
                }
            }

            List<List<List<String>>> page = new ArrayList<>();
            page.add(left);
            page.add(center);
            page.add(right);

            pages.add(page);
        }
        return pages;
    }
    public List<Map<String, String>> filterByTimeInterval(
            List<Map<String, String>> data,
            String dateTimeColumn,
            String pattern,
            long minutes
    ) {
        List<Map<String, String>> filtered = new ArrayList<>();
        if (data.size() <= 1) {
            System.out.println("ข้อมูลน้อยเกินไป (มีแต่ header) → ไม่ต้อง filter");
            return filtered;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);

        LocalDateTime prevTime = null;

        System.out.println("ตรวจสอบค่า DATE-TIME ทั้งหมดก่อน filter:");
        for (int i = 1; i < data.size(); i++) {
            Map<String, String> row = data.get(i);
            String dt = row.get(dateTimeColumn);
            System.out.println("Row " + i + " : " + dt);
        }

        for (int i = 1; i < data.size(); i++) {
            Map<String, String> row = data.get(i);
            String dt = row.get(dateTimeColumn);
            if (dt == null || dt.isEmpty()) {
                System.out.println("พบข้อมูลว่างใน DATE-TIME → ไม่ถูกนำมาใช้งาน (row " + i + ")");
                continue;
            }

            LocalDateTime currentTime;
            try {
                currentTime = LocalDateTime.parse(dt, formatter);
            } catch (Exception e) {
                System.out.println("Parse error ในแถว " + i + " : " + dt + " → ข้ามแถวนี้");
                continue;
            }

            if (prevTime == null) {
                filtered.add(row);
                prevTime = currentTime;
            } else {
                long diff = Duration.between(prevTime, currentTime).toMinutes();
                if (diff >= minutes) {
                    filtered.add(row);
                    prevTime = currentTime;
                }
            }
        }

        System.out.println("จำนวน rows หลัง filter = " + filtered.size());
        return filtered;
    }
    public List<Map<String, String>> getTripleTableRawData(String sheetId, String sheetName, String range) throws IOException {
        List<List<Object>> values = fetchSheetData(sheetId, sheetName, range);

        if (values == null || values.isEmpty()) {
            System.out.println("ไม่มีข้อมูลใน Google Sheet");
            return new ArrayList<>();
        }

        List<String> headers = new ArrayList<>();
        for (Object cell : values.get(0)) {
            headers.add(cell != null ? cell.toString().trim() : "");
        }

        System.out.println("Header ที่ดึงมาได้ (Raw):");
        System.out.println(headers);

        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < values.size(); i++) {
            List<Object> row = values.get(i);
            Map<String, String> rowMap = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                String key = headers.get(j);
                String value = (j < row.size()) ? row.get(j).toString().trim() : "";
                rowMap.put(key, value);
            }
            rows.add(rowMap);
        }
        return rows;
    }




    private LocalDateTime parseDateTime(String value, DateTimeFormatter formatter) {
        return LocalDateTime.parse(value, formatter);
    }

}