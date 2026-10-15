package lk.sliit.ems.util;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
import lk.sliit.ems.controller.ExportServlet;import java.util.Map;
class SecurityUtilitiesTest {
 @Test void passwordSaltAndVerification(){String a=PasswordUtil.hash("Demo@123"),b=PasswordUtil.hash("Demo@123");assertNotEquals(a,b);assertTrue(PasswordUtil.verify("Demo@123",a));assertFalse(PasswordUtil.verify("Wrong",a));}
 @Test void seedPassword(){assertTrue(PasswordUtil.verify("Demo@123","120000:4f70656e41495f47726f75703439:a74e1a5a5838ba1f838bb595c5469f779ef706758001c0fb479288f113cb1ba6"));}
 @Test void malformedHash(){assertFalse(PasswordUtil.verify("Demo@123","malformed"));}
 @Test void csvFormulaInjection(){assertEquals("\"'=1+1\"",ExportServlet.csv("=1+1"));assertEquals("\"'  @SUM(A1)\"",ExportServlet.csv("  @SUM(A1)"));assertEquals("\"quoted \"\"name\"\"\"",ExportServlet.csv("quoted \"name\""));}
 @Test void pdfPunctuation(){byte[] bytes=PdfUtil.payslip(Map.of("full_name","Chloé — O’Connor","net_pay","1.00"));String s=new String(bytes,java.nio.charset.Charset.forName("windows-1252"));assertTrue(s.contains("Chloé — O’Connor"));assertTrue(s.contains("/WinAnsiEncoding"));assertFalse(s.contains("2026-Y2-S1-KU-51"));}
 @Test void pdfUnicodeNames(){String name="அருண் — කොළඹ";byte[] bytes=PdfUtil.payslip(Map.of("full_name",name,"net_pay","1.00"));String s=new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1);assertTrue(s.contains("/ActualText <FEFF"));assertTrue(s.contains(java.util.HexFormat.of().formatHex(("Employee: "+name).getBytes(java.nio.charset.StandardCharsets.UTF_16BE))));assertTrue(s.contains(" c "));}
 @Test void pdfStructure(){byte[] bytes=PdfUtil.payslip(Map.of("full_name","Test (Name)","net_pay","87000.00"));String s=new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1);assertTrue(s.startsWith("%PDF-1.4"));assertTrue(s.endsWith("%%EOF"));assertTrue(s.contains("Test \\(Name\\)"));assertTrue(s.contains("87000.00"));}
}
