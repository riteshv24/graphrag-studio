import {escape as e, clamp} from './format.js';
/** Deterministic force layout: positions encode connectivity, not measured physical distance. */
export function layoutGraph(nodes, edges, width=920, height=510, steps=170) {
  const ordered=[...nodes].sort((a,b)=>String(a.id).localeCompare(String(b.id)));
  const degree=new Map(ordered.map(n=>[n.id,0]));
  for(const edge of edges){degree.set(edge.source,(degree.get(edge.source)||0)+1);degree.set(edge.target,(degree.get(edge.target)||0)+1);}
  const points=ordered.map((node,i)=>({...node,x:width/2+Math.cos(i*2.399963)*Math.sqrt((i+.5)/Math.max(1,ordered.length))*width*.37,y:height/2+Math.sin(i*2.399963)*Math.sqrt((i+.5)/Math.max(1,ordered.length))*height*.34,r:9+Math.min(9,Math.sqrt(degree.get(node.id)||0)*2),vx:0,vy:0}));
  const index=new Map(points.map(n=>[n.id,n]));
  for(let step=0;step<steps;step++) {
    for(let i=0;i<points.length;i++)for(let j=i+1;j<points.length;j++){
      const a=points[i],b=points[j],dx=a.x-b.x||.01,dy=a.y-b.y||.01,d2=Math.max(250,dx*dx+dy*dy),f=1800/d2;
      a.vx+=dx*f/Math.sqrt(d2);a.vy+=dy*f/Math.sqrt(d2);b.vx-=dx*f/Math.sqrt(d2);b.vy-=dy*f/Math.sqrt(d2);
    }
    for(const edge of edges){const a=index.get(edge.source),b=index.get(edge.target);if(!a||!b)continue;const dx=b.x-a.x,dy=b.y-a.y,d=Math.max(1,Math.hypot(dx,dy)),f=(d-145)*.004;a.vx+=dx/d*f;a.vy+=dy/d*f;b.vx-=dx/d*f;b.vy-=dy/d*f;}
    for(const p of points){p.vx=(p.vx+(width/2-p.x)*.0008)*.87;p.vy=(p.vy+(height/2-p.y)*.0015)*.87;p.x=clamp(p.x+p.vx,65,width-65);p.y=clamp(p.y+p.vy,45,height-40);}
  }
  return points.map(({vx,vy,...p})=>p);
}
export function neighbors(id, edges){return new Set(edges.flatMap(edge=>edge.source===id?[edge.target]:edge.target===id?[edge.source]:[]));}
export function visibleEdges(edges, includeWeak=false){return edges.filter(edge=>includeWeak||edge.type!=='CO_OCCURS');}
export function mountGraph(container, graph, {selected='',includeWeak=false,onSelect=()=>{}}={}) {
  if(!container)return;
  const edges=visibleEdges(graph.edges||[],includeWeak),points=layoutGraph(graph.nodes||[],edges),byId=new Map(points.map(n=>[n.id,n]));
  const connected=neighbors(selected,edges);let zoom=1,drag=null,moved=false;
  const kind=node=>/service/i.test(node.name)?'service':/team/i.test(node.name)?'team':/postgres|redis|kafka|database/i.test(node.name)?'data':'concept';
  container.innerHTML=`<svg class="graph-svg" viewBox="0 0 920 510" role="img" aria-label="Document knowledge graph. Select a labelled node to inspect its sources."><defs><pattern id="dots" width="24" height="24" patternUnits="userSpaceOnUse"><circle cx="1" cy="1" r=".75" fill="currentColor"/></pattern></defs><rect width="920" height="510" fill="url(#dots)" class="dot-field"/><g data-world><g data-edges>${edges.map(edge=>`<line data-edge="${e(edge.id)}" class="graph-edge ${edge.type==='CO_OCCURS'?'weak':''} ${selected&&(edge.source===selected||edge.target===selected)?'active':''}"><title>${e(edge.type)} · ${edge.sources?.length||0} source chunks</title></line>`).join('')}</g><g data-nodes>${points.map(node=>`<g class="graph-node ${kind(node)} ${node.id===selected?'selected':''} ${selected&&node.id!==selected&&!connected.has(node.id)?'dimmed':''}" data-node="${e(node.id)}" role="button" tabindex="0" aria-label="Inspect ${e(node.name)}"><circle class="node-halo" r="${node.r+6}"/><circle class="node-core" r="${node.r}"/><circle class="node-glint" r="3" cx="-3" cy="-3"/><text y="${node.r+21}" text-anchor="middle">${e(node.name)}</text><title>${e(node.name)} · ${node.mentions} mentions</title></g>`).join('')}</g></g></svg><div class="graph-controls"><button class="icon-button" data-zoom="out" aria-label="Zoom out">−</button><span data-zoom-label>100%</span><button class="icon-button" data-zoom="in" aria-label="Zoom in">+</button><button class="button tiny ghost" data-zoom="reset">Reset</button></div>`;
  const svg=container.querySelector('svg'),world=container.querySelector('[data-world]');
  function update(){container.querySelectorAll('[data-node]').forEach(el=>{const n=byId.get(el.dataset.node);el.setAttribute('transform',`translate(${n.x} ${n.y})`);});container.querySelectorAll('[data-edge]').forEach(el=>{const edge=edges.find(x=>x.id===el.dataset.edge),a=byId.get(edge.source),b=byId.get(edge.target);if(a&&b){el.setAttribute('x1',a.x);el.setAttribute('y1',a.y);el.setAttribute('x2',b.x);el.setAttribute('y2',b.y);}});world.setAttribute('transform',`translate(${460*(1-zoom)} ${255*(1-zoom)}) scale(${zoom})`);}
  update();
  container.querySelectorAll('[data-zoom]').forEach(button=>button.onclick=()=>{zoom=button.dataset.zoom==='reset'?1:clamp(zoom+(button.dataset.zoom==='in'?.2:-.2),.6,2);container.querySelector('[data-zoom-label]').textContent=`${Math.round(zoom*100)}%`;update();});
  container.querySelectorAll('[data-node]').forEach(el=>{
    el.addEventListener('pointerdown',event=>{drag=byId.get(el.dataset.node);moved=false;el.setPointerCapture(event.pointerId);});
    el.addEventListener('pointermove',event=>{if(!drag)return;const matrix=world.getScreenCTM();if(!matrix)return;const p=new DOMPoint(event.clientX,event.clientY).matrixTransform(matrix.inverse());if(Math.hypot(p.x-drag.x,p.y-drag.y)>3)moved=true;drag.x=clamp(p.x,35,885);drag.y=clamp(p.y,30,480);update();});
    el.addEventListener('pointerup',()=>{if(!drag)return;const id=drag.id;drag=null;if(!moved)onSelect(id);});
    el.addEventListener('pointercancel',()=>{drag=null;});
    el.addEventListener('keydown',event=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();onSelect(el.dataset.node);}});
  });
}
