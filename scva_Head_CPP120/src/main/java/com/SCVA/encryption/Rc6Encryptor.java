package com.SCVA.encryption;

import android.util.Base64;

import java.nio.charset.StandardCharsets;

public class Rc6Encryptor extends BaseEncryptor{
    private byte[] keyArr;
    public Rc6Encryptor(String key) {
        super(key);
        keyArr=stringToByte(key);
    }

    @Override
    public String encrypt(String plain) {
        byte[] textArr=plain.getBytes();

        byte[] encrypted=encrypt(textArr,keyArr);
        return new String(Base64.encode(encrypted,Base64.DEFAULT));
//        return new String(encrypted,StandardCharsets.UTF_8);
    }

    @Override
    public String decrypt(String cipher) {

        byte[] decrypted=decrypt(Base64.decode(cipher,Base64.DEFAULT),keyArr);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    @Override
    public int getEncryptorChunkSize() {
        return 15;
    }

    private  int w = 32, r = 20;
        private  int Pw = 0xb7e15163, Qw = 0x9e3779b9;
        private  int[] S;

//        public static void main(String[] args){
//
//            String plainText="salam 1200";
//            String userKey="1s34567890123456";
//
//            byte[] keyArr=stringToByte(userKey);
//            byte[] textArr=plainText.getBytes();
//
//            byte[] encrypted=encrypt(textArr,keyArr);
//            byte[] decrypted=decrypt(encrypted,keyArr);
//
//            String decryptedText = new String(decrypted, StandardCharsets.UTF_8);
//            Utils.println(decryptedText);
//        }

        private int[] convBytesWords(byte[] key, int u, int c) {
            int[] tmp = new int[c];
            for (int i = 0; i < tmp.length; i++)
                tmp[i] = 0;

            for (int i = 0, off = 0; i < c; i++)
                tmp[i] = ((key[off++] & 0xFF)) | ((key[off++] & 0xFF) << 8)
                        | ((key[off++] & 0xFF) << 16) | ((key[off++] & 0xFF) << 24);

            return tmp;
        }
        //////////////////////////////
        public int[] generateSubkeys(byte[] key) {

            int u = w / 8;
            int c = key.length / u;
            int t = 2 * r + 4;

            int[] L = convBytesWords(key, u, c);


            int[] S = new int[t];
            for (int i = 0 ; i<S.length;i++){
                S[i]=0;
            }
            S[0] = Pw;
            for (int i = 1; i < t; i++)
                S[i] = S[i - 1] + Qw;

            int A = 0;
            int B = 0;
            int k = 0, j = 0;

            int v = 3 * Math.max(c, t);

            for (int i = 0; i < v; i++) {
                A = S[k] = rotl((S[k] + A + B), 3);
                B = L[j] = rotl(L[j] + A + B, A + B);
                k = (k + 1) % t;
                j = (j + 1) % c;

            }

            return S;
        }
        /////////////////
        private  int rotl(int val, int pas) {
            return (val << pas) | (val >>> (32 - pas));
        }

        private  int rotr(int val, int pas) {
            return (val >>> pas) | (val << (32 - pas));
        }


        public  byte[] encryptBloc(byte[] input) {

            byte[] tmp = new byte[input.length];
            int t, u;
            int aux;
            int[] data = new int[input.length / 4];
            for (int i = 0; i < data.length; i++)
                data[i] = 0;
            int off = 0;
            for (int i = 0; i < data.length; i++) {
                data[i] = ((input[off++] & 0xff)) |
                        ((input[off++] & 0xff) << 8) |
                        ((input[off++] & 0xff) << 16) |
                        ((input[off++] & 0xff) << 24);
            }

            int A = data[0], B = data[1], C = data[2], D = data[3];

            B = B + S[0];
            D = D + S[1];
            for (int i = 1; i <= r; i++) {
                t = rotl(B * (2 * B + 1), 5);
                u = rotl(D * (2 * D + 1), 5);
                A = rotl(A ^ t, u) + S[2 * i];
                C = rotl(C ^ u, t) + S[2 * i + 1];

                aux = A;
                A = B;
                B = C;
                C = D;
                D = aux;
            }
            A = A + S[2 * r + 2];
            C = C + S[2 * r + 3];

            data[0] = A;
            data[1] = B;
            data[2] = C;
            data[3] = D;

            for (int i = 0; i < tmp.length; i++) {
                tmp[i] = (byte) ((data[i / 4] >>> (i % 4) * 8) & 0xff);
            }

            return tmp;
        }


        public byte[] encrypt(byte[] data, byte[] key) {

            byte[] bloc = new byte[16];



            S = generateSubkeys(key);


            int lenght = 16 - data.length % 16;
            byte[] padding = new byte[lenght];
            padding[0] = (byte) 0x80;

            for (int i = 1; i < lenght; i++)
                padding[i] = 0;
            int count = 0;
            byte[] tmp = new byte[data.length + lenght];
            int i;
            for (i = 0; i < data.length + lenght; i++) {
                if (i > 0 && i % 16 == 0) {
                    bloc = encryptBloc(bloc);
                    System.arraycopy(bloc, 0, tmp, i - 16, bloc.length);
                }

                if (i < data.length)
                    bloc[i % 16] = data[i];
                else {
                    bloc[i % 16] = padding[count];
                    count++;
                    if (count > lenght - 1) count = 1;
                }
            }
            bloc = encryptBloc(bloc);
            System.arraycopy(bloc, 0, tmp, i - 16, bloc.length);
            return tmp;
        }

        public  byte[] deletePadding(byte[] input){
            int count = 0;

            int i = input.length - 1;
            while (input[i] == 0) {
                count++;
                i--;
            }

            byte[] tmp = new byte[input.length - count - 1];
            System.arraycopy(input, 0, tmp, 0, tmp.length);
            return tmp;
        }



        public byte[] decryptBloc(byte[] input){
            byte[] tmp = new byte[input.length];
            int t,u;
            int aux;
            int[] data = new int[input.length/4];
            for(int i =0;i<data.length;i++)
                data[i] = 0;
            int off = 0;
            for(int i=0;i<data.length;i++){
                data[i] = 	((input[off++]&0xff))|
                        ((input[off++]&0xff) << 8) |
                        ((input[off++]&0xff) << 16) |
                        ((input[off++]&0xff) << 24);
            }


            int A = data[0],B = data[1],C = data[2],D = data[3];

            C = C - S[2*r+3];
            A = A - S[2*r+2];
            for(int i = r;i>=1;i--){
                aux = D;
                D = C;
                C = B;
                B = A;
                A = aux;

                u = rotl(D*(2*D+1),5);
                t = rotl(B*(2*B + 1),5);
                C = rotr(C-S[2*i + 1],t) ^ u;
                A = rotr(A-S[2*i],u) ^ t;
            }
            D = D - S[1];
            B = B - S[0];

            data[0] = A;data[1] = B;data[2] = C;data[3] = D;


            for(int i = 0;i<tmp.length;i++){
                tmp[i] = (byte)((data[i/4] >>> (i%4)*8) & 0xff);
            }

            return tmp;
        }
        ///////////////////////////////////////////////////
        public byte[] decrypt(byte[] data, byte[] key) {
            byte[] tmp = new byte[data.length];
            byte[] bloc = new byte[16];

            S=generateSubkeys(key);
            int i;
            for(i=0;i<data.length;i++){
                if(i>0 && i%16 == 0){
                    bloc = decryptBloc(bloc);
                    System.arraycopy(bloc, 0, tmp, i-16, bloc.length);
                }

                if (i < data.length)
                    bloc[i % 16] = data[i];
            }
            bloc =decryptBloc(bloc);
            System.arraycopy(bloc, 0, tmp, i - 16, bloc.length);

            tmp = deletePadding(tmp);
            return tmp;
        }
///////////////////////////////////

        public byte[] stringToByte(String keyFromUser){
            byte[] result = new byte[16];

            if(keyFromUser.length() >= 16) {
                keyFromUser = keyFromUser.substring(0, 15);
                for (int i = 0; i < keyFromUser.length(); i++) {
                    char c = keyFromUser.charAt(i);

                    byte tmp = 0;
                    switch (c) {
                        case '0':
                            tmp = 48;
                            break;
                        case '1':
                            tmp = 49;
                            break;
                        case '2':
                            tmp = 50;
                            break;
                        case '3':
                            tmp = 51;
                            break;
                        case '4':
                            tmp = 52;
                            break;
                        case '5':
                            tmp = 53;
                            break;
                        case '6':
                            tmp = 54;
                            break;
                        case '7':
                            tmp = 55;
                            break;
                        case '8':
                            tmp = 56;
                            break;
                        case '9':
                            tmp = 57;
                            break;
                        case 'A':
                            tmp = 65;
                            break;
                        case 'B':
                            tmp = 66;
                            break;
                        case 'C':
                            tmp = 67;
                            break;
                        case 'D':
                            tmp = 68;
                            break;
                        case 'E':
                            tmp = 69;
                            break;
                        case 'F':
                            tmp = 70;
                            break;
                        case 'G':
                            tmp = 71;
                            break;
                        case 'H':
                            tmp = 72;
                            break;
                        case 'I':
                            tmp = 73;
                            break;
                        case 'J':
                            tmp = 74;
                            break;
                        case 'K':
                            tmp = 75;
                            break;
                        case 'L':
                            tmp = 76;
                            break;
                        case 'M':
                            tmp = 77;
                            break;
                        case 'N':
                            tmp = 78;
                            break;
                        case 'O':
                            tmp = 79;
                            break;
                        case 'P':
                            tmp = 80;
                            break;
                        case 'Q':
                            tmp = 81;
                            break;
                        case 'R':
                            tmp = 82;
                            break;
                        case 'S':
                            tmp = 83;
                            break;
                        case 'T':
                            tmp = 84;
                            break;
                        case 'U':
                            tmp = 85;
                            break;
                        case 'V':
                            tmp = 86;
                            break;
                        case 'W':
                            tmp = 87;
                            break;
                        case 'X':
                            tmp = 88;
                            break;
                        case 'Y':
                            tmp = 89;
                            break;
                        case 'Z':
                            tmp = 90;
                            break;
                        case 'a':
                            tmp = 97;
                            break;
                        case 'b':
                            tmp = 98;
                            break;
                        case 'c':
                            tmp = 99;
                            break;
                        case 'd':
                            tmp = 100;
                            break;
                        case 'e':
                            tmp = 101;
                            break;
                        case 'f':
                            tmp = 102;
                            break;
                        case 'g':
                            tmp = 103;
                            break;
                        case 'h':
                            tmp = 104;
                            break;
                        case 'i':
                            tmp = 105;
                            break;
                        case 'j':
                            tmp = 106;
                            break;
                        case 'k':
                            tmp = 107;
                            break;
                        case 'l':
                            tmp = 108;
                            break;
                        case 'm':
                            tmp = 109;
                            break;
                        case 'n':
                            tmp = 110;
                            break;
                        case 'o':
                            tmp = 111;
                            break;
                        case 'p':
                            tmp = 112;
                            break;
                        case 'q':
                            tmp = 113;
                            break;
                        case 'r':
                            tmp = 114;
                            break;
                        case 's':
                            tmp = 115;
                            break;
                        case 't':
                            tmp = 116;
                            break;
                        case 'u':
                            tmp = 117;
                            break;
                        case 'v':
                            tmp = 118;
                            break;
                        case 'w':
                            tmp = 119;
                            break;
                        case 'x':
                            tmp = 120;
                            break;
                        case 'y':
                            tmp = 121;
                            break;
                        case 'z':
                            tmp = 122;
                            break;
                    }

                    result[i] = tmp;

                }

            }
            return result;
        }

}
