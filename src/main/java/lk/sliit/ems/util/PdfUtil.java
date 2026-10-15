package lk.sliit.ems.util;
import java.io.ByteArrayOutputStream;import java.nio.charset.StandardCharsets;import java.util.ArrayList;import java.util.List;import java.util.Map;
public final class PdfUtil{
 private PdfUtil(){}
 public static byte[] payslip(Map<String,Object> r){
  List<String> lines=List.of("PEOPLESPHERE EMPLOYEE MANAGEMENT SYSTEM","OFFICIAL PAYSLIP","","Employee: "+r.get("full_name"),"Employee code: "+r.get("employee_code"),"Pay period: "+r.get("period_label"),"Period dates: "+r.get("period_start")+" to "+r.get("period_end"),"","Base salary                 LKR "+r.get("base_salary"),"Gross pay                   LKR "+r.get("gross_pay"),"Employee EPF                LKR "+r.get("employee_epf"),"Other deductions            LKR "+r.get("total_deductions"),"NET PAY                     LKR "+r.get("net_pay"),"","Employer EPF               LKR "+r.get("employer_epf"),"ETF                         LKR "+r.get("etf"),"","Generated from finalized payroll data.");
  StringBuilder content=new StringBuilder();int y=768;
  var encoding=java.nio.charset.Charset.forName("windows-1252");
  for(String line:lines){
   if(!encoding.newEncoder().canEncode(line))content.append(PdfUnicode.line(line,y));
   else content.append("BT /F1 11 Tf 55 ").append(y).append(" Td (").append(escape(line)).append(") Tj ET ");
   y-=22;
  }
  String stream=content.toString();List<String> objects=new ArrayList<>();objects.add("<< /Type /Catalog /Pages 2 0 R >>");objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>");objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");objects.add("<< /Length "+stream.getBytes(java.nio.charset.Charset.forName("windows-1252")).length+" >>\nstream\n"+stream+"\nendstream");
  try{ByteArrayOutputStream out=new ByteArrayOutputStream();write(out,"%PDF-1.4\n");List<Integer> offsets=new ArrayList<>();offsets.add(0);for(int i=0;i<objects.size();i++){offsets.add(out.size());write(out,(i+1)+" 0 obj\n"+objects.get(i)+"\nendobj\n");}int xref=out.size();write(out,"xref\n0 "+(objects.size()+1)+"\n0000000000 65535 f \n");for(int i=1;i<offsets.size();i++)write(out,String.format("%010d 00000 n \n",offsets.get(i)));write(out,"trailer << /Size "+(objects.size()+1)+" /Root 1 0 R >>\nstartxref\n"+xref+"\n%%EOF");return out.toByteArray();}catch(Exception e){throw new IllegalStateException(e);}
 }
 private static String escape(String s){return s.replace("\\","\\\\").replace("(","\\(").replace(")","\\)");}
 private static void write(ByteArrayOutputStream out,String s)throws Exception{out.write(s.getBytes(java.nio.charset.Charset.forName("windows-1252")));}
}
