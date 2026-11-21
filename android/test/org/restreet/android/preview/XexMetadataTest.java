package org.restreet.android.preview;

public final class XexMetadataTest {
    public static void main(String[] args) {
        byte[] data = new byte[180];
        data[0]='X'; data[1]='E'; data[2]='X'; data[3]='2';
        put32(data,20,2);
        put32(data,24,0x00040006L); put32(data,28,64);
        put32(data,32,0x000103FFL); put32(data,36,100);

        put32(data,64,0x78AA9072L);
        put32(data,68,3); put32(data,72,3); put32(data,76,0x4541083BL);
        data[82]=1; data[83]=1;

        put32(data,100,72); put32(data,104,16); put32(data,108,1);
        byte[] name="xboxkrnl.exe".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(name,0,data,112,name.length);
        put32(data,128,44);
        data[166]=0; data[167]=1; // one import descriptor in this library

        XexMetadata parsed=XexMetadata.parse(data);
        check(parsed.xex2,"XEX2 header not recognized");
        check(parsed.executionInfoFound,"execution info not parsed");
        check("4541083B".equals(parsed.titleId),"wrong title ID");
        check("FIFA Street 3".equals(parsed.gameName),"wrong game name");
        check(parsed.importLibraries.size()==1,"import library count mismatch");
        check("xboxkrnl.exe".equals(parsed.importLibraries.get(0)),"import library name mismatch");

        byte[] malformed=new byte[24];
        malformed[0]='X'; malformed[1]='E'; malformed[2]='X'; malformed[3]='2';
        put32(malformed,20,0xFFFFFFFFL);
        check(!XexMetadata.parse(malformed).executionInfoFound,"malformed header accepted");
        System.out.println("XEX metadata and import-library tests passed");
    }

    private static void put32(byte[] b,int p,long value) {
        b[p]=(byte)(value>>>24); b[p+1]=(byte)(value>>>16);
        b[p+2]=(byte)(value>>>8); b[p+3]=(byte)value;
    }
    private static void check(boolean ok,String message) {
        if(!ok) throw new AssertionError(message);
    }
}
