package com.t.tshow.domain.event.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 이미지의 공공누리(KOGL) 유형. 한국관광공사 TourAPI 가 이미지마다 저작권 유형(Type1, Type3 등)을 준다.
 * 공공누리 유형의 뜻은 정해진 제도라서 설정이 아니라 여기에 둔다:
 * 제1유형 출처표시 / 제2유형 출처표시+상업적 이용금지 / 제3유형 출처표시+변경금지 / 제4유형 출처표시+상업적 이용금지+변경금지.
 * 변경금지 유형의 이미지는 자르거나 흐리게 하는 것도 변형이 될 수 있어 화면에서 원본 그대로 보여 준다.
 */
public final class ImageLicense {

    private static final Pattern TYPE = Pattern.compile("(?i)type\\s*([1-4])");

    private final int type;

    private ImageLicense(int type) {
        this.type = type;
    }

    /** 코드(Type3 등)를 해석한다. 공공누리 유형이 아니거나 모르면 null */
    public static ImageLicense of(String code) {
        if (code == null) return null;
        Matcher m = TYPE.matcher(code);
        return m.find() ? new ImageLicense(Integer.parseInt(m.group(1))) : null;
    }

    /** 이미지를 변형(자르기·흐림 등)하면 안 되는 유형 */
    public static boolean noModify(String code) {
        ImageLicense license = of(code);
        return license != null && license.noModify();
    }

    public boolean noModify() {
        return type == 3 || type == 4;
    }

    /** 화면에 보일 이용 조건 (예: 공공누리 제3유형(출처표시·변경금지)) */
    public String label() {
        return switch (type) {
            case 1 -> "공공누리 제1유형(출처표시)";
            case 2 -> "공공누리 제2유형(출처표시·상업적 이용금지)";
            case 3 -> "공공누리 제3유형(출처표시·변경금지)";
            default -> "공공누리 제4유형(출처표시·상업적 이용금지·변경금지)";
        };
    }
}
