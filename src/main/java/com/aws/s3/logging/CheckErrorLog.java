package com.aws.s3.logging;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

public class CheckErrorLog {
	/*  AWS S3 bucket 설정   */
	private static final Regions clientRegion = Regions.AP_NORTHEAST_2;
	private static final String bucketName = "my_elb_name"; // bucket 이름
	private static final String accessKey = "my_access_key"; // IAM access key
	private static final String secretKey = "my_secret_key"; // IAM secret key
	private static final String objectPath = "AWSLogs/000000000/elasticloadbalancing/ap-northeast-2"; //access log path
	
	/*  Excel 파일 설정   */
	private static final String fileName = bucketName+"-elb-error"; // 저장파일이름
	private static final int randomStrLength = 8; // 덮어쓰기 방지 random str 개수
	private static final String sheetName = "AWS ELB Error Response"; // sheet 이름
	private static final int columnNumber = 5; // column 개수
	private static final String[] reportHeaderColumnNames = {"NO","TIME","REQUEST URL","CLIENT IP","ELB CODE"}; // column 헤더 정보
	private static final Integer[] columnWidth = {4,8,24,8,8}; // column 넓이
	private static XSSFWorkbook workbook = new XSSFWorkbook(); // .xlsx 파일
	
	/*  전역 변수   */
	private static Scanner sc = new Scanner(System.in);
	private static List<String> listErrorLine = new ArrayList<>();
    private static List<HttpErrorModel> listErrorModel = new ArrayList<>();
	private static AtomicLong objectCnt = new AtomicLong(1); // 탐색 오브젝트 개수
	private static AtomicLong lineCnt = new AtomicLong(); // 탐색 줄 개수
	private static AtomicLong noCnt = new AtomicLong(); // 에러 id
	private static int errorCnt; // 총 에러 개수
	private static DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static DateFormat formatter_ymd = new SimpleDateFormat("yyyy-MM-dd");
	static {
		formatter.setTimeZone(TimeZone.getTimeZone("GMT+0900"));
		formatter_ymd.setTimeZone(TimeZone.getTimeZone("GMT+0900"));
	}
	private static Calendar cal = Calendar.getInstance();
	
	public static void main(String[] args) throws IOException, ParseException {		
		/*  탐색 조건 입력   */
		System.out.println("Searching ELB-Access-Log in S3 bucket by jinubb");
		System.out.print("Input Year : ");
        String year = sc.nextLine();
        System.out.print("Input Month : ");
        String month = sc.nextLine();
        System.out.print("Input Day : ");
        String day = sc.nextLine();
        System.out.print("View Simple Log? (Y/N) [Default : Y] : ");
        String viewSimple = sc.nextLine().toUpperCase();
        System.out.print("Save Excel(.xlsx)? (Y/N) [Default : Y] : ");
        String saveExcel = sc.nextLine().toUpperCase();
        if(viewSimple.isEmpty()) {
        	viewSimple = "Y";
        }
        if(saveExcel.isEmpty()) {
        	saveExcel = "Y";
        }
        if(day.length() == 1) {
        	day = "0"+day;
        }
        String objectPrefix = String.format("%s/%s/%s/%s",objectPath,year,month,day) ; 
        
        /*  오류 탐색   */
        AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
        		.withRegion(clientRegion)
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
                .build();
        ListObjectsV2Result result = s3Client.listObjectsV2(bucketName, objectPrefix);
        List<S3ObjectSummary> objects = result.getObjectSummaries();
        
        for (S3ObjectSummary os : objects) {
        	S3Object s3Object = s3Client.getObject(bucketName, os.getKey());	
        	Scanner fileIn = new Scanner(new GZIPInputStream(s3Object.getObjectContent()));
            if (fileIn != null) {
            	lineCnt.set(0L);
                while (fileIn.hasNext()) {
                	String line = fileIn.nextLine();
                	String[] lineData = line.split(" ");
                	if(!lineData[8].equals("200")) { // elb res code != 200
            			listErrorLine.add(line);
            			listErrorModel.add(convertErrorModel(lineData));
                	}
                    System.out.format("Searcing.. file count : %s, line count : %s\n",objectCnt.get(), lineCnt.incrementAndGet());
                }objectCnt.getAndIncrement();
            }
        }
        
		/*  탐색 결과 출력   */
        System.out.format("%s Error log %s-%s-%s\n",bucketName,year,month, day);
        if(viewSimple.equals("Y")) {
        	errorCnt = listErrorModel.size();
        	for(HttpErrorModel errorModel : listErrorModel) {
        		System.out.println(errorModel.printError());
        	}
        }else {
        	errorCnt = listErrorLine.size();
        	for(String errorLine : listErrorLine) {
            	System.out.println(errorLine);
            }
        }
        System.out.format("Searching ELB Error Response Counts : %d\n", errorCnt);
        
        /* Excel(.xlsx)파일 저장 */
        if(saveExcel.equals("Y")) {
        	Sheet sheet = workbook.createSheet(sheetName); // 시트명 설정
    		// 전역변수
    		Cell cell = null;
    		Row row = null;
    		int rowIdx = 0;
    		
    		// Cell style
    		CellStyle titleCellStyle = getTitleCellStyle(); // title
    		CellStyle headerCellStyle = getHeaderCellStyle(); // header
    		CellStyle basicCellStyle = getBasicCellStyle(); // basic
    		
    		// Title cell 병합
    		CellRangeAddress rangeAddress = new CellRangeAddress(0,0,0,4);
    		sheet.addMergedRegion(rangeAddress);
    		
    		// Column 간격 설정
    		for (int i=0;i<columnNumber;i++) {
    			sheet.setColumnWidth(i, columnWidth[i] * 1024);
    		}
    		
    		// Title
    		row = sheet.createRow(rowIdx++);
    		row.setHeight((short)500);
    		cell = row.createCell(0);
    		cell.setCellValue(String.format("REPORT DATE : %s-%s-%s", year,month,day));
    		cell.setCellStyle(titleCellStyle);
    		
    		// Header
    		row = sheet.createRow(rowIdx++);
    		row.setHeight((short)700);
    		for(int i=0;i<columnNumber;i++) {
    			cell = row.createCell(i);
    			cell.setCellValue(reportHeaderColumnNames[i]);
    			cell.setCellStyle(headerCellStyle);
    		}
    		
    		// Value
    		Iterator<HttpErrorModel> it = listErrorModel.iterator();
    		while(it.hasNext()) {
    			HttpErrorModel errorModel = it.next();
    			row = sheet.createRow(rowIdx++);
    			int cellIdx = 0;
    			
    	        cell = row.createCell(cellIdx++);
    	        cell.setCellValue(errorModel.getNo());
    	        cell.setCellStyle(basicCellStyle);

    	        cell = row.createCell(cellIdx++);
    	        cell.setCellValue(formatter.format(errorModel.getTime()));
    	        cell.setCellStyle(basicCellStyle);
    	        
    	        cell = row.createCell(cellIdx++);
    	        cell.setCellValue(errorModel.getRequestUrl());
    	        cell.setCellStyle(basicCellStyle);
    	        
    	        cell = row.createCell(cellIdx++);
    	        cell.setCellValue(errorModel.getClientIp());
    	        cell.setCellStyle(basicCellStyle);
    	        
    	        cell = row.createCell(cellIdx++);
    	        cell.setCellValue(Integer.valueOf(errorModel.getElbCode()));
    	        cell.setCellStyle(basicCellStyle);
    		}
    		
    		//엑셀 저장
            System.out.println(String.format("[%s] File generated!!", saveFile(year, month, day)));
        }
    }
	
	private static String saveFile(String year, String month, String day) throws IOException {
		String xlsxFilepath = "./"; //경로
		String randomStr = UUID.randomUUID().toString().replaceAll("-", "").substring(0, randomStrLength);
		String xlsxFileName = String.format("%s-%s-%s_%s_%s.xlsx", year,month,day,fileName,randomStr);
        File xlsxFile = new File(xlsxFilepath + xlsxFileName); //저장경로 설정
        FileOutputStream fileOut = new FileOutputStream(xlsxFile);
        workbook.write(fileOut);
        return xlsxFileName;
	}

	private static CellStyle getBasicCellStyle() {
		CellStyle basicCellStyle = workbook.createCellStyle();
		basicCellStyle.setVerticalAlignment(CellStyle.VERTICAL_CENTER);
		basicCellStyle.setAlignment(CellStyle.ALIGN_CENTER);
		basicCellStyle.setWrapText(true);
		basicCellStyle.setBorderLeft(CellStyle.BORDER_THIN);
		basicCellStyle.setBorderRight(CellStyle.BORDER_THIN);
		basicCellStyle.setBorderTop(CellStyle.BORDER_THIN);
		basicCellStyle.setBorderBottom(CellStyle.BORDER_THIN);
		return basicCellStyle;
	}
	
	private static CellStyle getTitleCellStyle() {
		Font titleFont = workbook.createFont();
		titleFont.setFontName("monospaced");
		titleFont.setColor(IndexedColors.BLACK.getIndex());
		titleFont.setFontHeightInPoints(((short)17));
		CellStyle headerCellStyle = getBasicCellStyle();
		headerCellStyle.setFont(titleFont);
		return headerCellStyle;
	}
	
	private static CellStyle getHeaderCellStyle() {
		Font headerFont = workbook.createFont();
		headerFont.setFontName("monospaced");
		headerFont.setColor(IndexedColors.BLACK.getIndex());
		headerFont.setFontHeightInPoints(((short)21));
		CellStyle headerCellStyle = getBasicCellStyle();
		headerCellStyle.setFont(headerFont);
		return headerCellStyle;
	}

	//에러모델로 변환
	private static HttpErrorModel convertErrorModel(String[] lineData) throws ParseException {
		HttpErrorModel errorModel = new HttpErrorModel();
		errorModel.setNo(noCnt.incrementAndGet());
		String tempTime = lineData[1].substring(0, 19).replaceAll("T", " ");
		Date dt = formatter.parse(tempTime);
		errorModel.setTime(changeGmtDate(dt));
		errorModel.setRequestUrl(lineData[12]+" "+lineData[13]+" "+lineData[14]);
		errorModel.setClientIp(lineData[3]);
		errorModel.setElbCode(lineData[8]);
		return errorModel;
	}

	//한국시간으로 변경
	private static Date changeGmtDate(Date utcDate) {
		cal.setTime(utcDate);
		cal.add(Calendar.HOUR, 9);
		return cal.getTime();
	}
}
