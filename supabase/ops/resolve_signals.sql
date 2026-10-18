create or replace function public.ar_try_resolve_signal(p_signal_id bigint)
returns void language plpgsql set search_path=public,pg_temp as $$
declare s record; matched record; link text;
begin
  select * into s from public.ar_marketplace_signals where id=p_signal_id;
  if not found or s.resolution_status='resolved' then return; end if;
  link := coalesce(nullif(s.resolved_url,''),substring(concat_ws(' ',s.title,s.content) from 'https://[^[:space:]<>"]+'));
  link := regexp_replace(coalesce(link,''),'[),.;]+$','','g');
  link := split_part(split_part(link,'#',1),'?',1);
  if link='' then
    update public.ar_marketplace_signals set resolution_status='needs_listing_link',
      resolution_confidence=0,resolution_method='listing_link_required',
      last_resolution_attempt_at=now(),updated_at=now() where id=s.id;
    return;
  end if;
  select id,url into matched from public.ar_deals where active=true and source_id=s.source_id and url=link limit 1;
  if matched.id is null then
    select id,url into matched from public.ar_deals where active=true and source_id=s.source_id
      and split_part(split_part(url,'#',1),'?',1)=link limit 1;
  end if;
  if matched.id is not null then
    update public.ar_marketplace_signals set resolution_status='resolved',resolved_url=matched.url,
      resolved_deal_id=matched.id,resolution_confidence=100,resolution_method='exact_listing_url',
      last_resolution_attempt_at=now(),updated_at=now() where id=s.id;
  else
    update public.ar_marketplace_signals set resolution_status='pending_detail',resolved_url=link,
      resolution_confidence=0,resolution_method='exact_link_awaiting_listing',
      last_resolution_attempt_at=now(),updated_at=now() where id=s.id;
  end if;
end;
$$;
create or replace function public.ar_try_resolve_pending_signals_for_source(p_source_id text)
returns void language plpgsql set search_path=public,pg_temp as $$
declare r record;
begin
  for r in select id from public.ar_marketplace_signals
    where source_id=p_source_id and coalesce(resolution_status,'new')<>'resolved'
    and (resolved_url is not null or concat_ws(' ',title,content) like '%https://%')
    order by created_at desc limit 20
  loop perform public.ar_try_resolve_signal(r.id); end loop;
end;
$$;
create or replace function public.ar_deal_after_write_resolver()
returns trigger language plpgsql set search_path=public,pg_temp as $$
declare r record;
begin
  for r in select id from public.ar_marketplace_signals
    where source_id=new.source_id and resolved_url=new.url
      and coalesce(resolution_status,'new')<>'resolved'
  loop perform public.ar_try_resolve_signal(r.id); end loop;
  return new;
end;
$$;
create or replace function public.ar_signal_after_write_resolver()
returns trigger language plpgsql set search_path=public,pg_temp as $$
begin
  perform public.ar_try_resolve_signal(new.id);
  return new;
end;
$$;
