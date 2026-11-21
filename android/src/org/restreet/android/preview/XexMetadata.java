package org.restreet.android.preview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Bounds-checked reader for XEX2 metadata; does not decrypt or decompress the PE image. */
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

    public final boolean xex2, executionInfoFound, fileFormatFound;
    public final String titleId, mediaId, version, baseVersion, gameName;
    public final int discNumber, discCount, encryptionType, compressionType;
    public final long headerSize, securityInfoOffset, entryPoint, imageBase, lzxWindowSize, firstLzxBlockSize, imageSize, loadAddress;
    public final boolean encryptedImageKeyPresent, securityInfoValid, firstLzxBlockHashPresent;
    public final List<ImportLibrary> importLibraries;

    private XexMetadata(boolean valid, boolean found, String title, String media,
                        String version, String base, int disc, int discs, String name,
                        List<ImportLibrary> imports, boolean formatFound, int encryption,
                        int compression, long header, long security, long entry, long image,
                        long window, long firstBlock, long peSize, long load, boolean keyPresent, boolean securityValid, boolean hashPresent) {
        xex2=valid; executionInfoFound=found; titleId=title; mediaId=media;
        this.version=version; baseVersion=base; discNumber=disc; discCount=discs;
        gameName=name; importLibraries=Collections.unmodifiableList(imports);
        fileFormatFound=formatFound; encryptionType=encryption; compressionType=compression;
        headerSize=header; securityInfoOffset=security; entryPoint=entry;
        imageBase=image; lzxWindowSize=window; firstLzxBlockSize=firstBlock; imageSize=peSize; loadAddress=load;
        encryptedImageKeyPresent=keyPresent; securityInfoValid=securityValid; firstLzxBlockHashPresent=hashPresent;
    }

    public static XexMetadata parse(byte[] b) {
        if (b == null || b.length < 24 || b[0]!='X' || b[1]!='E' || b[2]!='X' || b[3]!='2')
            return empty(false);
        long header=u32(b,8), security=u32(b,16), count=u32(b,20);
        if (count > 4096 || 24L+count*8L > b.length) return empty(true);
        String title="", media="", version="", base="", name="";
        int disc=0, discs=0, encryption=-1, compression=-1;
        long entry=-1, image=-1, window=0, firstBlock=0, peSize=0, load=0;
        boolean keyPresent=false, securityValid=false, hashPresent=false;
        boolean found=false, formatFound=false;
        List<ImportLibrary> imports=Collections.emptyList();
        for (int i=0;i<(int)count;i++) {
            int p=24+i*8;
            long key=u32(b,p), value=u32(b,p+4);
            if (key==0x00040006L && value <= b.length-24) {
                int o=(int)value;
                media=hex32(b,o); version=hex32(b,o+4); base=hex32(b,o+8);
                title=hex32(b,o+12); disc=b[o+18]&255; discs=b[o+19]&255;
                name=title.equals("4541083B") ? "FIFA Street 3"
                        : title.equals("4541097B") ? "FIFA Street (2012)" : "";
                found=true;
            } else if (key==0x000103FFL && value <= b.length-12) {
                imports=parseImportLibraries(b,(int)value);
            } else if (key==0x000003FFL && value <= b.length-8) {
                int o=(int)value;
                long size=u32(b,o);
                if (size>=8 && size<=b.length-o) {
                    encryption=u16(b,o+4);
                    compression=u16(b,o+6);
                    if (compression==2 && size>=12) window=u32(b,o+8);
                    if (compression==2 && size>=16) firstBlock=u32(b,o+12);
                    hashPresent = compression==2 && size>=36;
                    formatFound=true;
                }
            } else if (key==0x00010100L) {
                entry=value;
            } else if (key==0x00010201L) {
                image=value;
            }
        }
        if (security > 0 && security <= b.length - 0x160L) {
            int sec=(int)security;
            long secHeader=u32(b,sec);
            peSize=u32(b,sec+4);
            load=u32(b,sec+0x110);
            securityValid=secHeader>0 && secHeader<=b.length && peSize>0;
            for (int i=0;i<16;i++) if (b[sec+0x150+i]!=0) { keyPresent=true; break; }
        }
        return new XexMetadata(true,found,title,media,version,base,disc,discs,name,
                imports,formatFound,encryption,compression,header,security,entry,image,window,
                firstBlock,peSize,load,keyPresent,securityValid,hashPresent);
    }

    private static XexMetadata empty(boolean valid) {
        return new XexMetadata(valid,false,"","","","",0,0,"",
                new ArrayList<ImportLibrary>(),false,-1,-1,0,0,-1,-1,0,0,0,0,false,false,false);
    }

    private static List<ImportLibrary> parseImportLibraries(byte[] b,int offset) {
        long totalSize=u32(b,offset), stringSize=u32(b,offset+4), libraryCount=u32(b,offset+8);
        if (totalSize < 12 || totalSize > b.length-offset || libraryCount > 32
                || stringSize > totalSize-12) return Collections.emptyList();
        int stringsStart=offset+12;
        int stringsEnd=(int)(stringsStart+stringSize);
        List<String> names=new ArrayList<>();
        int cursor=stringsStart;
        for(int i=0;i<(int)libraryCount;i++) {
            if(cursor>=stringsEnd) return Collections.emptyList();
            int end=cursor;
            while(end<stringsEnd && b[end]!=0) end++;
            if(end==stringsEnd) return Collections.emptyList();
            names.add(new String(b,cursor,end-cursor,java.nio.charset.StandardCharsets.US_ASCII));
            cursor=(end+4)&~3;
        }
        long record=(long)stringsEnd;
        long tableEnd=(long)offset+totalSize;
        List<ImportLibrary> result=new ArrayList<>();
        for(int i=0;i<(int)libraryCount;i++) {
            if(record>tableEnd-40) return Collections.emptyList();
            int p=(int)record;
            long size=u32(b,p);
            int nameIndex=u16(b,p+36), imports=u16(b,p+38);
            if(size<40L+imports*4L || size>tableEnd-record || nameIndex>=names.size())
                return Collections.emptyList();
            List<Long> thunks=new ArrayList<>();
            for(int n=0;n<imports;n++) thunks.add(u32(b,p+40+n*4));
            result.add(new ImportLibrary(names.get(nameIndex),formatVersion(u32(b,p+28)),
                    formatVersion(u32(b,p+32)),imports,thunks));
            record+=size;
        }
        return result;
    }

    public String encryptionName() {
        if (!fileFormatFound) return "não informado";
        if (encryptionType==0) return "nenhuma";
        if (encryptionType==1) return "AES";
        return "desconhecida ("+encryptionType+")";
    }
    public String compressionName() {
        if (!fileFormatFound) return "não informado";
        if (compressionType==0) return "nenhuma";
        if (compressionType==1) return "basic";
        if (compressionType==2) return "normal (LZX)";
        if (compressionType==3) return "delta";
        return "desconhecida ("+compressionType+")";
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
