package org.restreet.android.preview;

import java.util.Locale;

/** Bounds-checked reader for the 24-byte XEX2 execution-info structure. */
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
        if (count > 4096 || 24L+count*8L > b.length)
            return new XexMetadata(true,false,"","","","",0,0,"");
        for (int i=0;i<(int)count;i++) {
            int p=24+i*8;
            if (u32(b,p)==0x00040006L) {
                long offset=u32(b,p+4);
                if (offset > b.length-24)
                    return new XexMetadata(true,false,"","","","",0,0,"");
                int o=(int)offset;
                String media=hex32(b,o);
                String version=hex32(b,o+4);
                String base=hex32(b,o+8);
                String title=hex32(b,o+12);
                int disc=b[o+18]&255, discs=b[o+19]&255;
                String name=title.equals("4541083B") ? "FIFA Street 3"
                        : title.equals("4541097B") ? "FIFA Street (2012)" : "";
                return new XexMetadata(true,true,title,media,version,base,disc,discs,name);
            }
        }
        return new XexMetadata(true,false,"","","","",0,0,"");
    }

    private static long u32(byte[] b,int p) {
        return ((long)(b[p]&255)<<24)|((long)(b[p+1]&255)<<16)
                |((long)(b[p+2]&255)<<8)|(long)(b[p+3]&255);
    }
    private static String hex32(byte[] b,int p) {
        return String.format(Locale.ROOT,"%08X",u32(b,p));
    }
}
