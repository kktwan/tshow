package com.t.tshow.global.util;


import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/** 공공 API 의 XML 응답을 읽는 작은 도우미 (외부 라이브러리 없이 JDK DOM 사용, DTD·외부 엔티티는 막는다) */
public final class Xml {

    private Xml() {
    }

    public static Element parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            return doc.getDocumentElement();
        } catch (Exception e) {
            throw new IllegalArgumentException("XML 을 읽지 못했어요: " + e.getMessage(), e);
        }
    }

    /** 하위 어디에 있든 이름이 tag 인 요소 전부 */
    public static List<Element> elements(Element parent, String tag) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) {
            result.add((Element) nodes.item(i));
        }
        return result;
    }

    /** 바로 아래 자식 중 이름이 tag 인 첫 요소의 글자. 없거나 비었으면 null */
    public static String text(Element parent, String tag) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && e.getTagName().equals(tag)) {
                String t = e.getTextContent();
                return Texts.blankToNull(t);
            }
        }
        return null;
    }

    public static Double number(Element parent, String tag) {
        String t = text(parent, tag);
        if (t == null) return null;
        try {
            return Double.valueOf(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
