package pe.gob.munisanmiguel.service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public final class GoogleMapsCoordinates {
    private GoogleMapsCoordinates(){}
    private static final Pattern PIN=Pattern.compile("!3d(-?\\d+(?:\\.\\d+)?)!4d(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern QUERY=Pattern.compile("[?&](?:q|query)=(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)");
    public static BigDecimal[] extract(String url){try{var uri=URI.create(url);String host=uri.getHost();if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||!(host.equals("google.com")||host.endsWith(".google.com")||host.equals("google.com.pe")||host.endsWith(".google.com.pe")))return null;
        String value=URLDecoder.decode(uri.toString(),StandardCharsets.UTF_8);var match=PIN.matcher(value);if(!match.find()){match=QUERY.matcher(value);if(!match.find())return null;}
        var lat=new BigDecimal(match.group(1));var lon=new BigDecimal(match.group(2));if(lat.abs().compareTo(BigDecimal.valueOf(90))>0||lon.abs().compareTo(BigDecimal.valueOf(180))>0)return null;return new BigDecimal[]{lat,lon};
    }catch(Exception e){return null;}}
}
