package org.restreet.android.preview;

public final class PeImportDirectoryTest {
    public static void main(String[] args) {
        byte[] pe = fixture();
        PowerPcPeImage image = PowerPcPeImage.parse(pe);
        check(image.valid, "fixture PE invalid: " + image.error);
        PeImportDirectory imports = PeImportDirectory.parse(pe, image);
        check(imports.valid, "import directory rejected: " + imports.error);
        check(imports.libraries.size() == 1, "library count mismatch");
        PeImportDirectory.Library library = imports.libraries.get(0);
        check("xam.xex".equals(library.name), "library name mismatch");
        check(library.symbols.size() == 2, "symbol count mismatch");
        check("XamGetSomething".equals(library.symbols.get(0).name), "named import mismatch");
        check(library.symbols.get(1).byOrdinal && library.symbols.get(1).ordinal == 5, "ordinal import mismatch");

        byte[] unmapped = fixture();
        put32(unmapped, 0x98 + 104, 0x3000);
        PowerPcPeImage unmappedImage = PowerPcPeImage.parse(unmapped);
        check(!PeImportDirectory.parse(unmapped, unmappedImage).valid, "unmapped import RVA accepted");

        byte[] truncated = fixture();
        put32(truncated, 0x98 + 104, 0x1100);
        put32(truncated, 0x98 + 108, 0x1000);
        check(!PeImportDirectory.parse(truncated, PowerPcPeImage.parse(truncated)).valid,
                "oversized import directory accepted");
        System.out.println("PE32 import directory tests passed");
    }

    private static byte[] fixture() {
        byte[] b = new byte[2048];
        b[0]='M'; b[1]='Z'; put32(b,0x3c,0x80);
        b[0x80]='P'; b[0x81]='E';
        put16(b,0x84,0x01F0); put16(b,0x86,1); put16(b,0x94,0xE0);
        int o=0x98;
        put16(b,o,0x10b); put32(b,o+16,0x1000); put32(b,o+28,0x82000000L);
        put32(b,o+56,0x3000); put32(b,o+60,0x200);
        put32(b,o+92,16); put32(b,o+104,0x1100); put32(b,o+108,40);
        int s=o+0xE0;
        byte[] name=".text".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(name,0,b,s,name.length);
        put32(b,s+8,0x400); put32(b,s+12,0x1000);
        put32(b,s+16,0x400); put32(b,s+20,0x200);
        // Import descriptor at RVA 0x1100 / file offset 0x300.
        put32(b,0x300,0x1150); put32(b,0x30c,0x1140); put32(b,0x310,0x1170);
        byte[] dll="xam.xex".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(dll,0,b,0x340,dll.length);
        put32(b,0x350,0x1160); put32(b,0x354,0x80000005L); put32(b,0x358,0);
        byte[] symbol="XamGetSomething".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(symbol,0,b,0x362,symbol.length);
        return b;
    }
    private static void put16(byte[] b,int p,int v){b[p]=(byte)v;b[p+1]=(byte)(v>>>8);}
    private static void put32(byte[] b,int p,long v){for(int i=0;i<4;i++)b[p+i]=(byte)(v>>>(8*i));}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
