package lk.sliit.ems.util;

import java.awt.Font;
import java.awt.font.*;
import java.awt.geom.PathIterator;
import java.io.InputStream;
import java.text.AttributedString;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;

/** Shaped vector outlines for names outside WinAnsi; uses only JDK text layout.
 * ActualText retains the original Unicode string for PDF extraction/accessibility. */
final class PdfUnicode {
    private static final Font LATIN=new Font(Font.SANS_SERIF,Font.PLAIN,11);
    private static final Font TAMIL=font("NotoSansTamil.ttf"), SINHALA=font("NotoSansSinhala.ttf");
    private PdfUnicode(){}
    private static Font font(String name){
        try(InputStream in=PdfUnicode.class.getResourceAsStream("/fonts/"+name)){
            if(in==null)throw new IllegalStateException("Missing payslip font: "+name);
            return Font.createFont(Font.TRUETYPE_FONT,in).deriveFont(11f);
        }catch(Exception e){throw new IllegalStateException("Unable to load payslip font",e);}
    }
    static String line(String text,int y){
        AttributedString attributed=new AttributedString(text);
        attributed.addAttribute(TextAttribute.FONT,LATIN);
        for(int i=0;i<text.length();i++){
            char ch=text.charAt(i);Font f=ch>=0x0B80 && ch<=0x0BFF?TAMIL:ch>=0x0D80 && ch<=0x0DFF?SINHALA:LATIN;
            if(f!=LATIN)attributed.addAttribute(TextAttribute.FONT,f,i,i+1);
        }
        TextLayout layout=new TextLayout(attributed.getIterator(),new FontRenderContext(null,true,true));
        double scale=Math.min(1,485.0/layout.getAdvance());
        StringBuilder out=new StringBuilder("/Span << /ActualText <FEFF"+HexFormat.of().formatHex(text.getBytes(StandardCharsets.UTF_16BE))+"> >> BDC q ");
        out.append(n(scale)).append(" 0 0 ").append(n(-scale)).append(" 55 ").append(y).append(" cm ");
        PathIterator path=layout.getOutline(null).getPathIterator(null);double[] v=new double[6];double x=0,z=0;
        while(!path.isDone()){
            switch(path.currentSegment(v)){
                case PathIterator.SEG_MOVETO -> {out.append(n(v[0])).append(' ').append(n(v[1])).append(" m ");x=v[0];z=v[1];}
                case PathIterator.SEG_LINETO -> {out.append(n(v[0])).append(' ').append(n(v[1])).append(" l ");x=v[0];z=v[1];}
                case PathIterator.SEG_QUADTO -> {out.append(n(x+(v[0]-x)*2/3)).append(' ').append(n(z+(v[1]-z)*2/3)).append(' ').append(n(v[2]+(v[0]-v[2])*2/3)).append(' ').append(n(v[3]+(v[1]-v[3])*2/3)).append(' ').append(n(v[2])).append(' ').append(n(v[3])).append(" c ");x=v[2];z=v[3];}
                case PathIterator.SEG_CUBICTO -> {for(double d:v)out.append(n(d)).append(' ');out.append("c ");x=v[4];z=v[5];}
                case PathIterator.SEG_CLOSE -> out.append("h ");
                default -> throw new IllegalStateException("Unexpected glyph path");
            }
            path.next();
        }
        return out.append(path.getWindingRule()==PathIterator.WIND_EVEN_ODD?"f* ":"f ").append("BT /F1 1 Tf 3 Tr 0 0 Td (x) Tj ET Q EMC ").toString();
    }
    private static String n(double d){return String.format(Locale.ROOT,"%.4f",d);}
}
