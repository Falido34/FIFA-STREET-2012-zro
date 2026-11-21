package org.restreet.android.preview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Bounds-checked reader for XEX2 execution info and import-library names. */
public final class XexMetadata {
    public final boolean xex2;
    public final boolean executionInfoFound;
    public final String titleId, mediaId, version, baseVersion, gameName;
    public final int discNumber, discCount;
    public final List<String> importLibraries;

    private XexMetadata(boolean valid, boolean found, String title, String media,
                        String version, String base, int disc, int discs, String name,
                        List<String> imports) {
        this.xex2=valid; this.executionInfoFound=found; this.titleId=title;
        this.mediaId=media; this.version=version; this.baseVersion=base;
        this.discNumber=disc; this.discCount=discs; this.gameName=name;
        this.importLibraries=Collections.unmodifiableList(imports);
    }

    public static XexMetadata parse(byte[] b) {
        if (b == null || b.length < 24 || b[0]!='X' || b[1]!='E' || b[2]!='X' || b[3]!='2')
            return empty(false);
        long count=u32(b,20);
        if (count > 4096 || 24L+count*8L > b.length) return empty(true);

        String title="", media="", version="", base="", name="";
        int disc=0, discs=0;
        boolean found=false;
        List<String> imports=Collections.emptyList();

        for (int i=0;i<(int)count;i++) {
            int p=24+i*8;
            long key=u32(b,p), offset=u32(b,p+4);
            if (key==0x00040006L && offset <= b.length-24) {
                int o=(int)offset;
                media=hex32(b,o);
                version=hex32(b,o+4);
                base=hex32(b,o+8);
                title=hex32(b,o+12);
                disc=b[o+18]&255; discs=b[o+19]&255;
                name=title.equals("4541083B") ? "FIFA Street 3"
                        : title.equals("4541097B") ? "FIFA Street (2012)" : "";
                found=true;
            } else if (key==0x000103FFL && offset <= b.length-12) {
                imports=parseImportLibraries(b,(int)offset);
            }
        }
        return new XexMetadata(true,found,title,media,version,base,disc,discs,name,imports);
    }

    private static XexMetadata empty(boolean valid) {
        return new XexMetadata(valid,false,"","","","",0,0,"",new ArrayList<String>());
    }

    private static List<String> parseImportLibraries(byte[] b,int offset) {
        long headerSize=u32(b,offset), stringSize=u32(b,offset+4), libraryCount=u32(b,offset+8);
        if (headerSize < 12 || headerSize > b.length-offset || stringSize > headerSize-12
                || libraryCount > 256) return Collections.emptyList();
        int stringsStart=offset+12;
        int librariesStart=(int)(stringsStart+stringSize);
        List<String> names=new ArrayList<>();
        int cursor=stringsStart;
        for(int i=0;i<(int)libraryCount;i++) {
            if(cursor>=librariesStart) return Collections.emptyList();
            int end=cursor;
            while(end<librariesStart && b[end]!=0) end++;
            if(end==librariesStart) return Collections.emptyList();
            String value=new String(b,cursor,end-cursor,java.nio.charset.StandardCharsets.US_ASCII);
            if(value.isEmpty()) return Collections.emptyList();
            names.add(value);
            cursor=(end+4)&~3;
        }
        long record=(long)librariesStart;
        for(int i=0;i<(int)libraryCount;i++) {
            if(record > b.length-40) return Collections.emptyList();
            long descriptorCount=((long)(b[(int)record+38]&255)<<8)|(b[(int)record+39]&255);
            record+=40L+descriptorCount*4L;
            if(record>b.length) return Collections.emptyList();
        }
        return names;
    }

    private static long u32(byte[] b,int p) {
        return ((long)(b[p]&255)<<24)|((long)(b[p+1]&255)<<16)
                |((long)(b[p+2]&255)<<8)|(long)(b[p+3]&255);
    }
    private static String hex32(byte[] b,int p) {
        return String.format(Locale.ROOT,"%08X",u32(b,p));
    }
}
