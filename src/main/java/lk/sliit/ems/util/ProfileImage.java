package lk.sliit.ems.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import lk.sliit.ems.service.Validation;

/** Decode bounded PNG/JPEG input, strip metadata and re-encode safe PNG bytes. */
public final class ProfileImage {
    private ProfileImage(){}
    public static byte[] normalize(byte[] input)throws IOException{
        Validation.require(input!=null && input.length>0 && input.length<=2*1024*1024,"Choose a JPEG or PNG image up to 2 MB.");
        try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(input))){
            var readers=ImageIO.getImageReaders(stream);
            Validation.require(readers.hasNext(),"File is not a supported image.");
            var reader=readers.next();
            try{reader.setInput(stream);
                String format=reader.getFormatName();
                Validation.require(format.equalsIgnoreCase("png") || format.equalsIgnoreCase("jpeg"),"Only JPEG and PNG images are accepted.");
                int w=reader.getWidth(0),h=reader.getHeight(0);
                Validation.require(w>0 && h>0 && w<=4096 && h<=4096 && (long)w*h<=16000000,"Image dimensions must not exceed 4096 pixels or 16 megapixels.");
                BufferedImage source=reader.read(0);double scale=Math.min(1,512.0/Math.max(w,h));
                BufferedImage safe=new BufferedImage(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)),BufferedImage.TYPE_INT_ARGB);
                var graphics=safe.createGraphics();try{graphics.drawImage(source,0,0,safe.getWidth(),safe.getHeight(),null);}finally{graphics.dispose();}
                var output=new ByteArrayOutputStream();ImageIO.write(safe,"png",output);return output.toByteArray();
            }finally{reader.dispose();}
        }
    }
}
