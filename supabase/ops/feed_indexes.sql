create index if not exists ar_deals_reviewed_feed_idx on public.ar_deals (vision_checked_at desc) where active=true and vision_status='checked';
create index if not exists ar_deals_profitable_feed_idx on public.ar_deals (conservative_profit_huf desc) where active=true and sold_comp_count>=3 and conservative_profit_huf>=15000 and score>=75;
create index if not exists ar_deals_vision_queue_order_idx on public.ar_deals ((source_id='vinted'),score desc,found_at desc) where active=true and cardinality(image_urls)>0 and vision_checked_at is null and score>=58 and price_huf between 500 and 250000;
