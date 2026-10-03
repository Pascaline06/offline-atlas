#include "../llama-lib/src/main/cpp/utf8_text.h"
#include <cassert>
int main() {
    const std::u16string text=u"東京, café, water ⛺ and 🧪";
    assert(atlas_text::utf16(atlas_text::utf8(text))==text);
    assert(atlas_text::utf8(u"🧪")=="\xf0\x9f\xa7\xaa");
    const std::u16string nul={u'a',0,u'b'};
    assert(atlas_text::utf16(atlas_text::utf8(nul))==nul);
    assert(atlas_text::utf16("\xf0\x80\x80\x80")[0]==0xfffd);
    assert(atlas_text::utf8(std::u16string(1,0xd800))=="\xef\xbf\xbd");
}
