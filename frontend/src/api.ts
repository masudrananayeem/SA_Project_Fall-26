const API = import.meta.env.VITE_API_URL || '';
const authorization = () => {
  const token = localStorage.getItem('mf_token') || '';
  if (token.trim()) return `Bearer ${token.trim()}`;
  const username = localStorage.getItem('mf_user') || '';
  const password = localStorage.getItem('mf_pass') || '';
  return username && password ? 'Basic ' + btoa(`${username}:${password}`) : '';
};
export async function api<T>(path:string, init:RequestInit={}):Promise<T>{
  const auth = authorization();
  const res=await fetch(`${API}/api${path}`,{...init,headers:{'Content-Type':'application/json',...(auth?{Authorization:auth}:{}),...(init.headers||{})}});
  if(!res.ok){let msg=`Request failed (${res.status})`;try{const p=await res.json();msg=p.detail||p.title||msg}catch{}throw new Error(msg)}
  return res.status===204?undefined as T:res.json();
}
