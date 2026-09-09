package com.pcopi.thaitv;

public class MainActivityV22 extends MainActivityV21 {
  @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
    super.onRequestPermissionsResult(requestCode,permissions,grantResults);
    if(requestCode==PERM) scanMedia();
  }
}
