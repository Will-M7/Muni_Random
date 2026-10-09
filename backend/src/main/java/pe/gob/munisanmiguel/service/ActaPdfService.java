package pe.gob.munisanmiguel.service;

import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.*;

@Service
public class ActaPdfService {
    private static final Charset WIN_ANSI=Charset.forName("windows-1252");
    private static final int LINES_PER_PAGE=42;

    public byte[] generar(Map<String,Object> acta) {
        List<Line> lines=new ArrayList<>();
        lines.add(new Line("MUNICIPALIDAD DISTRITAL DE SAN MIGUEL",Style.MUNI));
        lines.add(new Line("ACTA DE FISCALIZACIÓN",Style.TITLE));
        lines.add(new Line("Código: "+v(acta,"actaCodigo")+"     Expediente: "+v(acta,"expedienteCodigo"),Style.STRONG));
        lines.add(new Line("",Style.BODY));
        section(lines,"1. DATOS DE LA FISCALIZACIÓN");
        field(lines,"Persona fiscalizada",v(acta,"administrado"));
        field(lines,"Documento",v(acta,"documentoAdministrado"));
        field(lines,"Lugar",v(acta,"lugar"));
        field(lines,"Fecha",v(acta,"fecha"));
        boolean posterior=Boolean.TRUE.equals(acta.get("registroPosterior"));
        String source=v(acta,"origenHoraEjecucion");
        field(lines,posterior&&"PROGRAMADA".equals(source)?"Hora programada referencial":posterior?"Hora de ejecución declarada":"Hora de apertura",v(acta,"fechaInicio"));
        field(lines,posterior?"Instante de cierre del registro":"Hora de cierre",v(acta,"fechaCierre"));
        if(posterior){field(lines,"Registrado posteriormente en",v(acta,"registradoEn"));field(lines,"Registrado por",v(acta,"registradoPor"));}
        field(lines,"Objeto de fiscalización",v(acta,"objetoFiscalizacion"));
        field(lines,"Tipo de visita",v(acta,"tipoVisita"));
        section(lines,"2. FISCALIZADORES Y PARTICIPANTES");
        field(lines,"Fiscalizador responsable",v(acta,"fiscalizadorResponsable"));
        Object participants=acta.get("participantes");
        if(participants instanceof List<?> list) for(Object item:list) if(item instanceof Map<?,?> p){
            String name=join(str(p.get("nombre")),str(p.get("apellidos")));
            field(lines,str(p.get("tipoParticipante")),name+identity(p)+extra(p));
        }
        section(lines,"3. PERSONA PRESENTE");
        field(lines,"Situación",v(acta,"situacionPersona"));
        field(lines,"Nombre",join(v(acta,"personaNombres"),v(acta,"personaApellidos")));
        field(lines,"Documento",identity(acta));
        field(lines,"Condición",v(acta,"personaCondicion"));
        field(lines,"Observaciones",v(acta,"personaObservaciones"));
        section(lines,"4. HECHOS CONSTATADOS");
        paragraph(lines,v(acta,"hechosConstatados"));
        section(lines,"5. OCURRENCIAS");
        paragraph(lines,v(acta,"ocurrencias"));
        section(lines,"6. MANIFESTACIONES Y OBSERVACIONES");
        field(lines,"Persona fiscalizada",v(acta,"observacionesFiscalizado"));
        field(lines,"Fiscalizador",v(acta,"observacionesFiscalizador"));
        section(lines,"7. EVIDENCIAS ASOCIADAS");
        Object evidence=acta.get("evidencias");
        if(evidence instanceof List<?> list&&!list.isEmpty()) {
            for(Object item:list) if(item instanceof Map<?,?> e)
                field(lines,str(e.get("tipo")),str(e.get("nombreOriginal"))+emptyExtra(e.get("descripcion")));
        } else lines.add(new Line("Sin archivos de evidencia adjuntos.",Style.BODY));
        section(lines,"8. CONSTANCIA DE FIRMA");
        field(lines,"Estado",v(acta,"firmaFiscalizadoEstado"));
        field(lines,"Constancia",v(acta,"firmaFiscalizadoObservacion"));
        field(lines,"Fiscalizador responsable",v(acta,"fiscalizadorResponsable"));
        lines.add(new Line("Firma del fiscalizador: pendiente de firma manual",Style.BODY));
        lines.add(new Line("",Style.BODY));
        lines.add(new Line("Fiscalizador responsable: ______________________________________________",Style.BODY));
        lines.add(new Line("Persona presente: ____________________________________________________",Style.BODY));
        lines.add(new Line("",Style.BODY));
        lines.add(new Line("Registro objetivo de hechos y manifestaciones consignadas durante la diligencia.",Style.SMALL));

        List<List<Line>> pages=paginate(lines);
        return writePdf(pages,v(acta,"actaCodigo"));
    }

    private byte[] writePdf(List<List<Line>> pages,String code){
        int n=pages.size(),pagesObj=2,fontObj=3,boldObj=4,firstContent=5;
        int max=firstContent+n*2-1;List<byte[]> objects=new ArrayList<>(Collections.nCopies(max+1,null));
        objects.set(1,ascii("<< /Type /Catalog /Pages 2 0 R >>"));
        StringBuilder kids=new StringBuilder();for(int i=0;i<n;i++)kids.append(6+i*2).append(" 0 R ");
        objects.set(pagesObj,ascii("<< /Type /Pages /Kids ["+kids+"] /Count "+n+" >>"));
        objects.set(fontObj,ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
        objects.set(boldObj,ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"));
        for(int i=0;i<n;i++){
            int contentId=firstContent+i*2,pageId=contentId+1;
            byte[] stream=pageStream(pages.get(i),i+1,n,code);
            objects.set(contentId,concat(ascii("<< /Length "+stream.length+" >>\nstream\n"),stream,ascii("\nendstream")));
            objects.set(pageId,ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents "+contentId+" 0 R >>"));
        }
        ByteArrayOutputStream out=new ByteArrayOutputStream();write(out,ascii("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n"));
        long[] offsets=new long[max+1];
        for(int i=1;i<=max;i++){offsets[i]=out.size();write(out,ascii(i+" 0 obj\n"));write(out,objects.get(i));write(out,ascii("\nendobj\n"));}
        long xref=out.size();write(out,ascii("xref\n0 "+(max+1)+"\n0000000000 65535 f \n"));
        for(int i=1;i<=max;i++)write(out,ascii(String.format(Locale.ROOT,"%010d 00000 n \n",offsets[i])));
        write(out,ascii("trailer\n<< /Size "+(max+1)+" /Root 1 0 R >>\nstartxref\n"+xref+"\n%%EOF\n"));
        return out.toByteArray();
    }

    private byte[] pageStream(List<Line> lines,int page,int count,String code){
        StringBuilder s=new StringBuilder();s.append("q 0.08 0.31 0.22 rg 42 809 511 1 re f Q\n");
        if(page>1)text(s,48,790,9,"F2","ACTA DE FISCALIZACIÓN · "+code,"0.08 0.31 0.22");
        float y=page>1?766:790;
        for(Line line:lines){if(line.style==Style.SECTION){y-=5;s.append("q 0.92 0.95 0.92 rg 45 ").append(y-4).append(" 505 17 re f Q\n");text(s,51,y,10,"F2",line.value,"0.08 0.31 0.22");y-=20;continue;}
            int size=line.style==Style.TITLE?17:line.style==Style.MUNI?10:line.style==Style.SMALL?8:9;
            String font=line.style==Style.TITLE||line.style==Style.MUNI||line.style==Style.STRONG?"F2":"F1";
            String color=line.style==Style.TITLE?"0.58 0.43 0.12":line.style==Style.SMALL?"0.3 0.3 0.3":"0.12 0.16 0.14";
            text(s,51,y,size,font,line.value,color);y-=line.style==Style.TITLE?23:line.style==Style.MUNI?16:line.style==Style.SMALL?12:14;
        }
        s.append("q 0.08 0.31 0.22 rg 42 36 511 1 re f Q\n");
        text(s,48,22,8,"F1","Municipalidad Distrital de San Miguel · "+code,"0.3 0.3 0.3");
        text(s,500,22,8,"F1","Página "+page+" de "+count,"0.3 0.3 0.3");
        var encoded=WIN_ANSI.encode(s.toString());byte[] bytes=new byte[encoded.remaining()];encoded.get(bytes);return bytes;
    }
    private void text(StringBuilder s,float x,float y,int size,String font,String value,String color){s.append("BT ").append(color).append(" rg /").append(font).append(' ').append(size).append(" Tf ").append(x).append(' ').append(y).append(" Td (").append(escape(value)).append(") Tj ET\n");}
    private String escape(String value){byte[] b=value.getBytes(WIN_ANSI);StringBuilder s=new StringBuilder();for(byte z:b){int c=z&255;if(c=='('||c==')'||c=='\\')s.append('\\').append((char)c);else if(c<32||c>126)s.append('\\').append(String.format(Locale.ROOT,"%03o",c));else s.append((char)c);}return s.toString();}
    private List<List<Line>> paginate(List<Line> lines){List<List<Line>> result=new ArrayList<>();List<Line> page=new ArrayList<>();for(Line line:lines){List<String> wrapped=wrap(line.value,line.style==Style.SECTION?72:94);for(String str:wrapped){if(page.size()>=LINES_PER_PAGE){result.add(page);page=new ArrayList<>();}page.add(new Line(str,line.style));}}if(!page.isEmpty())result.add(page);return result;}
    private List<String> wrap(String s,int max){if(s==null||s.isBlank())return List.of("");List<String> out=new ArrayList<>();for(String para:s.split("\\R",-1)){StringBuilder row=new StringBuilder();for(String word:para.split("\\s+")){if(row.length()>0&&row.length()+word.length()+1>max){out.add(row.toString());row.setLength(0);}if(row.length()>0)row.append(' ');row.append(word);}out.add(row.toString());}return out;}
    private void section(List<Line> a,String s){a.add(new Line(s,Style.SECTION));}
    private void field(List<Line> a,String k,String v){String value=(v==null||v.isBlank()?"No consignado":v).trim();a.add(new Line(k+": "+value,Style.BODY));}
    private void paragraph(List<Line> a,String v){a.add(new Line(v==null||v.isBlank()?"No consignado.":v.trim(),Style.BODY));}
    private String identity(Map<?,?> m){String t=str(m.get("personaTipoDocumento")),n=str(m.get("personaNumeroDocumento"));return join(t,n);}
    private String identity(Object o){if(!(o instanceof Map<?,?> m))return "";return identity(m);}
    private String extra(Map<?,?> m){String entity=str(m.get("entidadArea")),role=str(m.get("cargoCondicion"));return emptyExtra(join(entity,role));}
    private String emptyExtra(Object v){String s=str(v);return s.isBlank()?"":" · "+s;}
    private String join(String a,String b){a=a==null?"":a.trim();b=b==null?"":b.trim();return a.isBlank()?b:b.isBlank()?a:a+" · "+b;}
    private String str(Object x){return x==null?"":String.valueOf(x);}
    private String v(Map<String,Object> m,String k){return str(m.get(k));}
    private static byte[] ascii(String s){return s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);}
    private static byte[] concat(byte[]... bs){int n=0;for(byte[] b:bs)n+=b.length;byte[] all=new byte[n];int p=0;for(byte[] b:bs){System.arraycopy(b,0,all,p,b.length);p+=b.length;}return all;}
    private static void write(ByteArrayOutputStream o,byte[] b){o.write(b,0,b.length);}
    private enum Style{MUNI,TITLE,STRONG,SECTION,BODY,SMALL}
    private record Line(String value,Style style){}
}
