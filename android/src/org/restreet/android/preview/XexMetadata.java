package org.restreet.android.preview;

import java.util.Locale;

/** Minimal, bounds-checked reader for XEX2 execution-info metadata. */
public final class XexMetadata {
    public final boolean xex2;
    public final boolean executionInfoFound;
    public final String titleId, mediaId, version, baseVersion, gameName;
    public final int discNumber, discCount;

    private XexMetadata(boolean valid, boolean found, String title, String media,
                        String version, String base, int disc, int discs, String name) {
        this.xex2=valid; this.executionInfoFound=found; this.titleId=title;
        this.mediaId=media; this.version=version; this.baseVersion=base;
        this.discNumber=disc; this.discCount=discs; this.gameName=name;
    }

    public static XexMetadata parse(byte[] b) {
        if (b == null || b.length < 24 || b[0]!='X' || b[1]!='E' || b[2]!='X' || b[3]!='2')
            return new XexMetadata(false,false,"","","","",0,0,"");
        long count=u32(b,20);
        if (count > 4096) return new XexMetadata(true,false,"","","","",0,0,"");
        if (24+(int)count*8 > b.length) return new XexMetadata(true,false,"","","","",0,0,"");
        for (int i=0;i<(int)count;i++) {
            int p=24+i*8;
            if (u32(b,p)==0x00040006L) {
                long offset=u32(b,p+4);
                if (offset > b.length-36) return new XexMetadata(true,false,"","","","",0,0,"");
                int o=(int)offset;
                StringBuilder media=new StringBuilder();
                for(int n=0;n<16;n++) media.append(String.format(Locale.ROOT,"%02X",b[o+n]&255));
                String version=hex32(b,o+16), base=hex32(b,o+20), title=hex32(b,o+24);
                int disc=b[o+30]&255, discs=b[o+31]&255;
                String name=title.equals("4541083B") ? "FIFA Street 3"
                        : title.equals("4541097B") ? "FIFA Street (2012)" : "";
                return new XexMetadata(true,true,title,media.toString(),version,base,disc,discs,name);
            }
        }
        return new XexMetadata(true,false,"","","","",0,0,"");
    }

    private static long u32(byte[] b,int p) {
        return ((long)(b[p]&255)<<24)|((long)(b[p+1]&255)<<16)|((long)(b[p+2]&255)<<8)|(long)(b[p+3]&255);
    }
    private static String hex32(byte[] b,int p) {
        return String.format(Locale.ROOT,"%08X",u32(b,p));
    }
}
