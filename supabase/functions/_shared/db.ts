import postgres from 'npm:postgres@3.4.3';
export const sql = postgres(Deno.env.get('SUPABASE_DB_URL')!, {prepare:false, max:2, idle_timeout:10, connect_timeout:10});
export const headers = {'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'apikey, content-type, authorization','Access-Control-Allow-Methods':'GET, POST, OPTIONS'};
export const response = (data: unknown, status = 200) => new Response(JSON.stringify(data), {status, headers});
export function publicKey(req: Request) {
  const key = req.headers.get('apikey');
  try { return !!key && Object.values(JSON.parse(Deno.env.get('SUPABASE_PUBLISHABLE_KEYS') ?? '{}')).includes(key); }
  catch { return false; }
}
