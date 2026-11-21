package org.restreet.android.preview;

public final class XexMetadataTest {
    public static void main(String[] args) {
        byte[] data = new byte[768];
        data[0]='X'; data[1]='E'; data[2]='X'; data[3]='2';
        put32(data,8,0x2000); put32(data,16,0x180); put32(data,20,5);
        put32(data,24,0x00040006L); put32(data,28,64);
        put32(data,32,0x000103FFL); put32(data,36,100);
        put32(data,40,0x00010100L); put32(data,44,0x82001000L);
        put32(data,48,0x00010201L); put32(data,52,0x82000000L);
        put32(data,56,0x000003FFL); put32(data,60,224);

        put32(data,64,0x78AA9072L);
        put32(data,68,3); put32(data,72,3); put32(data,76,0x4541083BL);
        data[82]=1; data[83]=1;

        // XEX import header: total size, string table size, library count.
        put32(data,100,124); put32(data,104,24); put32(data,108,2);
        byte[] first="xam.xex".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] second="xboxkrnl.exe".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(first,0,data,112,first.length);
        System.arraycopy(second,0,data,120,second.length);

        // Xex2ImportLibrary records: size, digest, id, versions, name index, import count.
        put32(data,136,44); put32(data,160,0x1111); put32(data,164,0x12345678L);
        put32(data,168,0x12345678L); put16(data,172,0); put16(data,174,1);
        put32(data,176,0x82000000L);
        put32(data,180,44); put32(data,204,0x2222); put32(data,208,0x23456789L);
        put32(data,212,0x23456789L); put16(data,216,1); put16(data,218,1);
        put32(data,220,0x82000004L);
        put32(data,224,36); put16(data,228,1); put16(data,230,2); put32(data,232,0x20000); put32(data,236,0x1000);

        put32(data,0x180,0x3000); put32(data,0x184,0x1A0000);
        put32(data,0x290,0x82000000L);
        for (int i=0;i<16;i++) data[0x2D0+i]=(byte)(i+1);

        XexMetadata parsed=XexMetadata.parse(data);
        check(parsed.xex2,"XEX2 header not recognized");
        check(parsed.executionInfoFound,"execution info not parsed");
        check("4541083B".equals(parsed.titleId),"wrong title ID");
        check("FIFA Street 3".equals(parsed.gameName),"wrong game name");
        check(parsed.importLibraries.size()==2,"import library count mismatch");
        check("xam.xex".equals(parsed.importLibraries.get(0).name),"wrong first library");
        check("xboxkrnl.exe".equals(parsed.importLibraries.get(1).name),"wrong second library");
        check(parsed.importLibraries.get(0).importCount==1,"wrong first import count");
        check(parsed.importLibraries.get(0).thunkAddresses.size()==1,"wrong thunk address count");
        check(parsed.importLibraries.get(0).thunkAddresses.get(0)==0x82000000L,"wrong thunk address");
        check("1.2.13398.120".equals(parsed.importLibraries.get(0).version),"wrong version decode");
        check(parsed.headerSize==0x2000,"wrong PE header size");
        check(parsed.securityInfoOffset==0x80,"wrong security info offset");
        check(parsed.entryPoint==0x82001000L,"wrong entry point");
        check(parsed.imageBase==0x82000000L,"wrong image base");
        check(parsed.fileFormatFound,"file format info missing");
        check("AES".equals(parsed.encryptionName()),"wrong encryption type");
        check("normal (LZX)".equals(parsed.compressionName()),"wrong compression type");
        check(parsed.lzxWindowSize==0x20000,"wrong LZX window size");
        check(parsed.firstLzxBlockSize==0x1000,"wrong first LZX block size");
        check(parsed.firstLzxBlockHashPresent,"first LZX block hash not detected");
        check(parsed.securityInfoValid,"security info not validated");
        check(parsed.imageSize==0x1A0000,"wrong PE image size");
        check(parsed.loadAddress==0x82000000L,"wrong security load address");
        check(parsed.encryptedImageKeyPresent,"encrypted AES session key not detected");

        byte[] malformed=new byte[24];
        malformed[0]='X'; malformed[1]='E'; malformed[2]='X'; malformed[3]='2';
        put32(malformed,20,0xFFFFFFFFL);
        check(!XexMetadata.parse(malformed).executionInfoFound,"malformed header accepted");
        System.out.println("XEX metadata and import-library tests passed");
    }

    private static void put16(byte[] b,int p,int value) {
        b[p]=(byte)(value>>>8); b[p+1]=(byte)value;
    }
    private static void put32(byte[] b,int p,long value) {
        b[p]=(byte)(value>>>24); b[p+1]=(byte)(value>>>16);
        b[p+2]=(byte)(value>>>8); b[p+3]=(byte)value;
    }
    private static void check(boolean ok,String message) {
        if(!ok) throw new AssertionError(message);
    }
}
