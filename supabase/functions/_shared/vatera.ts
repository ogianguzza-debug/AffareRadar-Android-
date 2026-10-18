function decode(s:string){return s.replace(/&amp;/g,'&').replace(/&quot;/g,'"').replace(/&#39;/g,"'").replace(/&nbsp;|&#160;/g,' ');}
function text(s:string){return decode(s.replace(/<[^>]*>/g,' ')).replace(/\s+/g,' ').trim();}
function amount(s:string){const m=text(s).match(/([0-9][0-9 .\u00a0]{0,14})\s*Ft\b/i);return m?Number(m[1].replace(/\D/g,'')):0;}
export function vateraListings(html:string){
 const all=[...html.matchAll(/<a\b[^>]*href\s*=\s*["'](https:\/\/www\.vatera\.hu\/[^"']*-(\d{8,})\.html(?:[^"']*)?)["'][^>]*>/gi)];
 const anchors=all.filter(x=>/\bproduct_link\b/i.test(x[0]));const out:any[]=[];
 for(let i=0;i<anchors.length;i++){
  const a=anchors[i],id=a[2],end=anchors[i+1]?.index??Math.min(html.length,a.index!+14000),section=html.slice(a.index!,end);
  const title=decode(a[0].match(/\btitle=["']([^"']+)["']/i)?.[1]??section.match(/<h3[^>]*>([\s\S]*?)<\/h3>/i)?.[1]??'');
  const fixed=section.match(/class=["'][^"']*fix-price[^"']*["'][\s\S]{0,1500}?class=["']originalVal["'][^>]*>([^<]+)<\/span>\s*Ft/i);
  const price=fixed?amount(fixed[1]+' Ft'):amount(section);
  const isAuction=!fixed&&/auction-price|Aukciós termékek/i.test(section);
  const imageRe=new RegExp('https://images\\.vatera\\.hu/[^,"\\s<>]*'+id+'[^,"\\s<>]*','g');
  const images=[...new Set((html.match(imageRe)??[]).map(u=>decode(u).replace(/resize\.width=\d+/,'resize.width=960')))].slice(0,6);
  if(title&&price>0)out.push({url:a[1].split(/[?#]/)[0],title:text(title),price,images,isAuction});
 }
 return [...new Map(out.map(x=>[x.url,x])).values()].slice(0,80);
}
