package org.restreet.android.preview;

public final class PowerPcPeImageTest {
    public static void main(String[] args) {
        byte[] pe=new byte[1024];
        pe[0]='M'; pe[1]='Z'; put32(pe,0x3c,0x80);
        pe[0x80]='P'; pe[0x81]='E';
        put16(pe,0x84,0x01F0); put16(pe,0x86,1); put16(pe,0x94,0xE0);
        int o=0x98;
        put16(pe,o,0x10b); put32(pe,o+16,0x1000); put32(pe,o+28,0x82000000L);
        put32(pe,o+56,0x3000); put32(pe,o+60,0x200);
        int s=o+0xE0;
        byte[] name=".text".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(name,0,pe,s,name.length);
        put32(pe,s+8,0x100); put32(pe,s+12,0x1000);
        put32(pe,s+16,0x100); put32(pe,s+20,0x200);
        PowerPcPeImage parsed=PowerPcPeImage.parse(pe);
        check(parsed.valid,"valid PPC PE rejected: "+parsed.error);
        check(parsed.powerPc,"PowerPC machine not recognized");
        byte[] bigEndianPpc=pe.clone(); put16(bigEndianPpc,0x84,0x01F2);
        check(PowerPcPeImage.parse(bigEndianPpc).valid,"PowerPC big-endian PE rejected");
        check(parsed.entryPoint==0x1000,"entry point mismatch");
        check(parsed.imageBase==0x82000000L,"image base mismatch");
        check(parsed.sections.size()==1 && ".text".equals(parsed.sections.get(0).name),"section parse failed");
        check(parsed.fileOffsetForRva(0x1000,pe.length)==0x200,"section RVA mapping failed");
        check(parsed.fileOffsetForRva(0x1010,pe.length)==0x210,"section offset mapping failed");
        check(parsed.fileOffsetForRva(0x3000,pe.length)==-1,"unmapped RVA accepted");
        check(parsed.fileOffsetForRva(0x1010,0x205)==-1,"out-of-file RVA accepted");
        byte[] wrong=pe.clone(); put16(wrong,0x84,0x8664);
        check(!PowerPcPeImage.parse(wrong).valid,"non-PPC image accepted");
        byte[] truncated=new byte[100]; truncated[0]='M'; truncated[1]='Z'; put32(truncated,0x3c,0x80);
        check(!PowerPcPeImage.parse(truncated).valid,"truncated image accepted");
        System.out.println("PowerPC PE image validation tests passed");
    }
    private static void put16(byte[] b,int p,int v){b[p]=(byte)v;b[p+1]=(byte)(v>>>8);}
    private static void put32(byte[] b,int p,long v){for(int i=0;i<4;i++)b[p+i]=(byte)(v>>>(8*i));}
    private static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);}
}
