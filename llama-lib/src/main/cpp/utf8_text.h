#pragma once
#include <string>
#include <cstdint>

namespace atlas_text {
inline std::string utf8(const std::u16string &text) {
    std::string out;
    for(size_t i=0;i<text.size();++i) {
        uint32_t cp=text[i];
        if(cp>=0xd800 && cp<=0xdbff && i+1<text.size() && text[i+1]>=0xdc00 && text[i+1]<=0xdfff)
            cp=0x10000+((cp-0xd800)<<10)+(text[++i]-0xdc00);
        else if(cp>=0xd800 && cp<=0xdfff) cp=0xfffd;
        if(cp<0x80) out.push_back(static_cast<char>(cp));
        else if(cp<0x800) {out.push_back(static_cast<char>(0xc0|(cp>>6)));out.push_back(static_cast<char>(0x80|(cp&63)));}
        else if(cp<0x10000) {out.push_back(static_cast<char>(0xe0|(cp>>12)));out.push_back(static_cast<char>(0x80|((cp>>6)&63)));out.push_back(static_cast<char>(0x80|(cp&63)));}
        else {out.push_back(static_cast<char>(0xf0|(cp>>18)));out.push_back(static_cast<char>(0x80|((cp>>12)&63)));out.push_back(static_cast<char>(0x80|((cp>>6)&63)));out.push_back(static_cast<char>(0x80|(cp&63)));}
    }
    return out;
}
inline std::u16string utf16(const std::string &text) {
    std::u16string out;
    for(size_t i=0;i<text.size();) {
        unsigned char first=text[i];uint32_t cp=first;size_t length=1;uint32_t minimum=0;
        if(first>=0xc2 && first<=0xdf) {cp=first&31;length=2;minimum=0x80;}
        else if(first>=0xe0 && first<=0xef) {cp=first&15;length=3;minimum=0x800;}
        else if(first>=0xf0 && first<=0xf4) {cp=first&7;length=4;minimum=0x10000;}
        else if(first>=0x80) {out.push_back(0xfffd);++i;continue;}
        bool valid=i+length<=text.size();
        for(size_t j=1;valid && j<length;++j) {
            unsigned char next=text[i+j];
            if((next&0xc0)!=0x80) valid=false;
            else cp=(cp<<6)|(next&63);
        }
        if(!valid || cp<minimum || cp>0x10ffff || (cp>=0xd800 && cp<=0xdfff)) {out.push_back(0xfffd);++i;continue;}
        i+=length;
        if(cp<=0xffff) out.push_back(static_cast<char16_t>(cp));
        else {cp-=0x10000;out.push_back(static_cast<char16_t>(0xd800+(cp>>10)));out.push_back(static_cast<char16_t>(0xdc00+(cp&1023)));}
    }
    return out;
}
}
