const API = import.meta.env.VITE_API_URL || '';
const credentials = () => {
  const username = localStorage.getItem('mf_user') || import.meta.env.VITE_API_USERNAME || '';
  const password = localStorage.getItem('mf_pass') || import.meta.env.VITE_API_PASSWORD || '';
  return username && password ? 'Basic ' + btoa(`${username}:${password}`) : '';
};
export async function api<T>(path:string, init:RequestInit={}):Promise<T>{
  const auth = credentials();
  const res=await fetch(`${API}/api${path}`,{...init,headers:{'Content-Type':'application/json',...(auth?{Authorization:auth}:{}),...(init.headers||{})}});
  if(!res.ok){let msg=`Request failed (${res.status})`;try{const p=await res.json();msg=p.detail||p.title||msg}catch{}throw new Error(msg)}
  return res.status===204?undefined as T:res.json();
}
