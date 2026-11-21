package org.restreet.android.preview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounds-checked validator for a decompressed Xbox 360 PowerPC PE32 image. */
public final class PowerPcPeImage {
    public static final int MACHINE_POWERPC = 0x01F0;
    public static final int MACHINE_POWERPC_BE = 0x01F2;
    public static final class Section {
        public final String name;
        public final long virtualSize, virtualAddress, rawSize, rawOffset, characteristics;
        Section(String n,long vs,long va,long rs,long ro,long c) {
            name=n; virtualSize=vs; virtualAddress=va; rawSize=rs; rawOffset=ro; characteristics=c;
        }
    }
    public final boolean valid, powerPc, pe32;
    public final String error;
    public final int machine, sectionCount;
    public final long entryPoint, imageBase, imageSize, headersSize;
    public final List<Section> sections;

    private PowerPcPeImage(boolean ok,String err,int mach,int count,long entry,long base,
                           long size,long headers,List<Section> list) {
        valid=ok; error=err; machine=mach; powerPc=mach==MACHINE_POWERPC || mach==MACHINE_POWERPC_BE;
        sectionCount=count; pe32=true; entryPoint=entry; imageBase=base;
        imageSize=size; headersSize=headers;
        sections=Collections.unmodifiableList(list);
    }

    public static PowerPcPeImage parse(byte[] b) {
        if (b==null || b.length<64) return bad("Imagem menor que o cabeçalho DOS.");
        if (b[0]!='M' || b[1]!='Z') return bad("Assinatura MZ ausente.");
        long peOff=u32le(b,0x3c);
        if (!range(peOff,24,b.length)) return bad("Offset do cabeçalho PE fora da imagem.");
        int p=(int)peOff;
        if (b[p]!='P'||b[p+1]!='E'||b[p+2]!=0||b[p+3]!=0) return bad("Assinatura PE inválida.");
        int machine=u16le(b,p+4), count=u16le(b,p+6), optionalSize=u16le(b,p+20);
        if (count<1 || count>96) return bad("Quantidade de seções PE inválida.");
        long opt=(long)p+24;
        if (!range(opt,optionalSize,b.length) || optionalSize<96) return bad("Cabeçalho opcional PE truncado.");
        int o=(int)opt;
        if (u16le(b,o)!=0x10b) return bad("Imagem não é PE32.");
        long entry=u32le(b,o+16), base=u32le(b,o+28);
        long imageSize=u32le(b,o+56), headers=u32le(b,o+60);
        if (imageSize==0 || headers==0 || headers>b.length) return bad("Tamanhos PE inválidos.");
        long table=opt+optionalSize, tableSize=(long)count*40;
        if (!range(table,tableSize,b.length)) return bad("Tabela de seções PE truncada.");
        List<Section> sections=new ArrayList<>();
        for(int i=0;i<count;i++) {
            int q=(int)(table+i*40L);
            int n=0; while(n<8 && b[q+n]!=0)n++;
            String name=new String(b,q,n,java.nio.charset.StandardCharsets.US_ASCII);
            long vs=u32le(b,q+8), va=u32le(b,q+12), rs=u32le(b,q+16);
            long ro=u32le(b,q+20), ch=u32le(b,q+36);
            if (rs>0 && !range(ro,rs,b.length)) return bad("Dados da seção "+name+" fora da imagem.");
            if (va>imageSize || vs>imageSize-va) return bad("Endereço virtual da seção "+name+" fora da imagem.");
            sections.add(new Section(name,vs,va,rs,ro,ch));
        }
        if (machine!=MACHINE_POWERPC && machine!=MACHINE_POWERPC_BE) return new PowerPcPeImage(false,
                "Máquina PE não é PowerPC Xbox 360.",machine,count,entry,base,imageSize,headers,sections);
        return new PowerPcPeImage(true,"",machine,count,entry,base,imageSize,headers,sections);
    }

    private static PowerPcPeImage bad(String e) {
        return new PowerPcPeImage(false,e,0,0,0,0,0,0,new ArrayList<Section>());
    }
    private static boolean range(long off,long len,long total) {
        return off>=0 && len>=0 && off<=total && len<=total-off;
    }
    private static int u16le(byte[] b,int p) { return (b[p]&255)|((b[p+1]&255)<<8); }
    private static long u32le(byte[] b,int p) {
        return (b[p]&255L)|((b[p+1]&255L)<<8)|((b[p+2]&255L)<<16)|((b[p+3]&255L)<<24);
    }
}
