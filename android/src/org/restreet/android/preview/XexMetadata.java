package org.restreet.android.preview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Bounds-checked reader for XEX2 execution info and import-library tables. */
public final class XexMetadata {
    public static final class ImportLibrary {
        public final String name, version, minVersion;
        public final int importCount;
        public final List<Long> thunkAddresses;
        ImportLibrary(String n,String v,String min,int count,List<Long> thunks) {
            name=n; version=v; minVersion=min; importCount=count;
            thunkAddresses=Collections.unmodifiableList(thunks);
        }
    }

    public final boolean xex2, executionInfoFound;
    public final String titleId, mediaId, version, baseVersion, gameName;
    public final int discNumber, discCount;
    public final List<ImportLibrary> importLibraries;

    private XexMetadata(boolean valid, boolean found, String title, String media,
                        String version, String base, int disc, int discs, String name,
                        List<ImportLibrary> imports) {
        xex2=valid; executionInfoFound=found; titleId=title; mediaId=media;
        this.version=version; baseVersion=base; discNumber=disc; discCount=discs;
        gameName=name; importLibraries=Collections.unmodifiableList(imports);
    }

    public static XexMetadata parse(byte[] b) {
        if (b == null || b.length < 24 || b[0]!='X' || b[1]!='E' || b[2]!='X' || b[3]!='2')
            return empty(false);
        long count=u32(b,20);
        if (count > 4096 || 24L+count*8L > b.length) return empty(true);
        String title="", media="", version="", base="", name="";
        int disc=0, discs=0;
        boolean found=false;
        List<ImportLibrary> imports=Collections.emptyList();
        for (int i=0;i<(int)count;i++) {
            int p=24+i*8;
            long key=u32(b,p), offset=u32(b,p+4);
            if (key==0x00040006L && offset <= b.length-24) {
                int o=(int)offset;
                media=hex32(b,o); version=hex32(b,o+4); base=hex32(b,o+8);
                title=hex32(b,o+12); disc=b[o+18]&255; discs=b[o+19]&255;
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
        return new XexMetadata(valid,false,"","","","",0,0,"",new ArrayList<ImportLibrary>());
    }

    private static List<ImportLibrary> parseImportLibraries(byte[] b,int offset) {
        long headerSize=u32(b,offset), stringSize=u32(b,offset+4), libraryCount=u32(b,offset+8);
        if (headerSize < 12 || headerSize > 64 || headerSize > b.length-offset
                || libraryCount > 256 || stringSize > b.length-offset-headerSize)
            return Collections.emptyList();
        int stringsStart=(int)(offset+headerSize);
        int recordsStart=(int)(stringsStart+stringSize);
        List<ImportLibrary> result=new ArrayList<>();
        long record=recordsStart;
        for(int i=0;i<(int)libraryCount;i++) {
            if(record > b.length-40) return Collections.emptyList();
            int p=(int)record;
            long size=u32(b,p);
            int imports=u16(b,p+38), nameIndex=u16(b,p+36);
            if(size < 40L+imports*4L || size > b.length-record || nameIndex >= stringSize)
                return Collections.emptyList();
            String libraryName=readCString(b,stringsStart+nameIndex,recordsStart);
            if(libraryName==null || libraryName.isEmpty()) return Collections.emptyList();
            List<Long> thunks=new ArrayList<>();
            for(int n=0;n<imports;n++) thunks.add(u32(b,p+40+n*4));
            long version=u32(b,p+28), minVersion=u32(b,p+32);
            result.add(new ImportLibrary(libraryName,formatVersion(version),
                    formatVersion(minVersion),imports,thunks));
            record += size;
        }
        return result;
    }

    private static String readCString(byte[] b,int start,int limit) {
        if(start<0 || start>=limit || limit>b.length) return null;
        int end=start;
        while(end<limit && b[end]!=0) end++;
        if(end==limit) return null;
        return new String(b,start,end-start,java.nio.charset.StandardCharsets.US_ASCII);
    }
    private static String formatVersion(long v) {
        return ((v>>>28)&15)+"."+((v>>>24)&15)+"."+((v>>>8)&65535)+"."+(v&255);
    }
    private static int u16(byte[] b,int p) { return ((b[p]&255)<<8)|(b[p+1]&255); }
    private static long u32(byte[] b,int p) {
        return ((long)(b[p]&255)<<24)|((long)(b[p+1]&255)<<16)
                |((long)(b[p+2]&255)<<8)|(long)(b[p+3]&255);
    }
    private static String hex32(byte[] b,int p) {
        return String.format(Locale.ROOT,"%08X",u32(b,p));
    }
}
