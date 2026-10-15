package lk.sliit.ems.util;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
class ProfileImageTest {
 @Test void acceptsAndResizesPng()throws Exception{var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(1024,600,BufferedImage.TYPE_INT_RGB),"png",out);byte[] png=ProfileImage.normalize(out.toByteArray());var image=ImageIO.read(new ByteArrayInputStream(png));assertEquals(512,image.getWidth());assertEquals(300,image.getHeight());}
 @Test void acceptsJpegReencodesPng()throws Exception{var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(30,30,BufferedImage.TYPE_INT_RGB),"jpeg",out);byte[] png=ProfileImage.normalize(out.toByteArray());assertEquals(0x89,png[0]&255);assertEquals('P',png[1]);}
 @Test void rejectsScriptAndSvg(){assertThrows(IllegalArgumentException.class,()->ProfileImage.normalize("<svg onload='alert(1)'/>".getBytes()));}
 @Test void rejectsOversize(){assertThrows(IllegalArgumentException.class,()->ProfileImage.normalize(new byte[2097153]));}
 @Test void rejectsHugeDimensions()throws Exception{var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(4100,1,BufferedImage.TYPE_INT_RGB),"png",out);assertThrows(IllegalArgumentException.class,()->ProfileImage.normalize(out.toByteArray()));}
}
