export type SoldListing={title:string;price:number;url:string};
function plain(s:string){return s.replace(/<[^>]*>/g,' ').replace(/&nbsp;|&#160;/g,' ').replace(/&amp;/g,'&').replace(/\s+/g,' ').trim();}
export function soldListings(html:string,base='https://galeriasavaria.hu'):SoldListing[]{
 const out:SoldListing[]=[];
 // Search cards hide sale prices for signed-out visitors. Never read shipping,
 // recommendations or the following card as the sale price of this object.
 const headings=[...html.matchAll(/<h3\b[^>]*>([\s\S]*?)<\/h3>/gi)];
 for(let i=0;i<headings.length;i++){
  const h=headings[i],link=h[1].match(/<a\b[^>]*href=["'](\/termekek\/reszletek\/[^"']+)["'][^>]*>([\s\S]*?)<\/a>/i);
  if(!link)continue;
  const body=html.slice(h.index!+h[0].length,headings[i+1]?.index??Math.min(html.length,h.index!+5000));
  const block=body.match(/<span\b[^>]*class=["'][^"']*\bproduct-price\b[^"']*["'][^>]*>([\s\S]*?)<\/span>/i);
  const priceText=block?plain(block[1]):'',m=priceText.match(/([0-9][0-9 .\u00a0]*)\s*(?:HUF|Ft)\b/i);
  const price=m?Number(m[1].replace(/\D/g,'')):0;
  if(price>0&&price<1e9)out.push({title:plain(link[2]),price,url:new URL(link[1],base).toString().split(/[?#]/)[0]});
 }
 return [...new Map(out.map(x=>[x.url,x])).values()].slice(0,80);
}
