package com.focuslock.app;
public class AnnouncementPolicyTest {
  static void expect(boolean actual, boolean expected) { if (actual != expected) throw new AssertionError(); }
  public static void main(String[] args) {
    expect(AnnouncementPolicy.shouldShow(true,true,true,"v1","v1",""),true);
    expect(AnnouncementPolicy.shouldShow(true,true,true,"v1","v1","v1"),false);
    expect(AnnouncementPolicy.shouldShow(true,true,true,"v2","v1","v1"),true);
    expect(AnnouncementPolicy.shouldShow(true,true,false,"v1","v1",""),false);
    expect(AnnouncementPolicy.shouldShow(true,true,false,"v2","v1","v1"),true);
    expect(AnnouncementPolicy.shouldShow(false,true,true,"v1","",""),false);
    expect(AnnouncementPolicy.shouldShow(true,false,true,"v1","",""),false);
    System.out.println("Announcement policy tests passed");
  }
}
