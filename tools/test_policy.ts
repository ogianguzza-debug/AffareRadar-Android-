import assert from 'node:assert/strict';
import {alertEligible,clientDeal,object} from '../supabase/functions/_shared/policy.ts';
const good = {id:'known',active:true,title:'Lampada design',price_huf:5000,estimated_low_huf:50000,
  source_id:'example',source_name:'Example',score:85,vision_status:'checked',vision_confidence:85,
  sold_comp_count:3,conservative_profit_huf:20000,vision_red_flags:[],vision_missing_photos:[],
  raw:{valueEvidence:{comparables:[{url:'a'},{url:'b'},{url:'c'}]}},found_at:'2025-01-01'};
assert.equal(alertEligible(good),true,'older active listings must remain eligible');
assert.equal(alertEligible({...good,vision_status:null}),false,'a textual score cannot replace a photo review');
assert.equal(alertEligible({...good,raw:{}}),false,'aggregated numbers without comparable references are insufficient');
assert.equal(alertEligible({...good,vision_red_flags:['suspected replica']}),false);
assert.equal(alertEligible({...good,vision_missing_photos:['serial number']}),false);
assert.equal(alertEligible({...good,is_auction:true}),false,'auction bids cannot be treated as purchase prices');
assert.equal(alertEligible({...good,title:'Herend porcelain'}),false);
assert.equal(alertEligible({...good,conservative_profit_huf:-100}),false);
assert.deepEqual(object(JSON.stringify(JSON.stringify({a:1}))),{a:1});
const mapped=clientDeal(good);
assert.equal(mapped.sourceId,'example'); assert.equal(mapped.prudentValueHuf,50000);
assert.equal(mapped.alertEligible,true); assert.equal(mapped.reviewStatus,'opportunity');
console.log('Evidence policy: 13 checks passed');
