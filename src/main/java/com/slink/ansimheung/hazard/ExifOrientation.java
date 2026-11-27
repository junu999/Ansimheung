package com.slink.ansimheung.hazard;

/**
 * JPEG EXIF 의 Orientation(0x0112) 값만 읽는다 (#24). 사진을 다시 저장하면 EXIF 가 모두 사라지므로,
 * 휴대폰 사진이 누워 보이지 않게 저장 전에 픽셀을 돌려 두기 위한 것. 새 라이브러리 없이 APP1 → TIFF IFD0 만 본다.
 * 거울 반전 값(2·4·5·7)은 드물어서 회전하지 않는다.
 */
final class ExifOrientation {

    private static final int TAG_ORIENTATION = 0x0112;

    private ExifOrientation() {
    }

    /** 시계 방향 90° 회전 횟수: Orientation 3 → 2, 6 → 1, 8 → 3, 그 외(없음 포함) → 0 */
    static int quarterTurnsClockwise(byte[] jpeg) {
        return switch (orientation(jpeg)) {
            case 3 -> 2;
            case 6 -> 1;
            case 8 -> 3;
            default -> 0;
        };
    }

    /** 1~8, 못 찾으면 1 (그대로) */
    static int orientation(byte[] d) {
        int i = 2;   // SOI(FFD8) 다음
        while (i + 4 <= d.length) {
            if ((d[i] & 0xFF) != 0xFF) {
                return 1;
            }
            int marker = d[i + 1] & 0xFF;
            if (marker == 0xFF) {   // 채움 바이트
                i++;
                continue;
            }
            if (marker == 0xDA || marker == 0xD9) {   // 영상 데이터 시작·끝 → 메타데이터 구간 끝
                return 1;
            }
            int length = u16(d, i + 2, false);
            int start = i + 4;
            int end = i + 2 + length;
            if (length < 2 || end > d.length) {
                return 1;
            }
            if (marker == 0xE1 && isExifHeader(d, start, end)) {
                return fromTiff(d, start + 6, end);
            }
            i = end;
        }
        return 1;
    }

    private static boolean isExifHeader(byte[] d, int start, int end) {
        return end - start >= 6 && d[start] == 'E' && d[start + 1] == 'x' && d[start + 2] == 'i' && d[start + 3] == 'f'
                && d[start + 4] == 0 && d[start + 5] == 0;
    }

    private static int fromTiff(byte[] d, int base, int end) {
        if (base + 8 > end) {
            return 1;
        }
        boolean little;
        if (d[base] == 'I' && d[base + 1] == 'I') {
            little = true;
        } else if (d[base] == 'M' && d[base + 1] == 'M') {
            little = false;
        } else {
            return 1;
        }
        long ifdOffset = u32(d, base + 4, little);
        if (ifdOffset < 8 || base + ifdOffset + 2 > end) {
            return 1;
        }
        int ifd = base + (int) ifdOffset;
        int count = u16(d, ifd, little);
        for (int k = 0; k < count; k++) {
            int entry = ifd + 2 + k * 12;
            if (entry + 12 > end) {
                return 1;
            }
            if (u16(d, entry, little) == TAG_ORIENTATION) {
                int value = u16(d, entry + 8, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static int u16(byte[] d, int i, boolean little) {
        int a = d[i] & 0xFF;
        int b = d[i + 1] & 0xFF;
        return little ? (b << 8) | a : (a << 8) | b;
    }

    private static long u32(byte[] d, int i, boolean little) {
        long hi = u16(d, little ? i + 2 : i, little);
        long lo = u16(d, little ? i : i + 2, little);
        return (hi << 16) | lo;
    }
}
