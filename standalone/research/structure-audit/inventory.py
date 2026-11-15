"""Read-only file/import inventory for the standalone layout review.

Writes only this audit's outputs. Static import reachability is an overapproximation
(includes conditional/lazy imports), not proof a function executes on every solve.
"""
import argparse
import ast
from collections import Counter, defaultdict
import csv
import hashlib
import json
import os
from pathlib import Path
import subprocess


BASE='src/parkour_solver/'
# This is a reviewed proposal, not an automatic rename instruction. Semicolon
# destinations mean extract responsibilities before moving the old module.
ROOT_MAP={
 '__init__':('runtime_api','api.py; __init__.py'),
 'block_contacts':('runtime_split','constraints/contacts.py; search/repair.py; validation/vertical.py'),
 'block_profiles':('runtime','movement/profiles.py'),
 'block_search':('runtime_split','scene/normalize.py; search/support_profiles.py; api.py'),
 'blockages':('runtime','constraints/avoidance.py'),
 'candidate_comparison':('runtime','search/comparison.py'),
 'case_profiles':('runtime','movement/cases.py'),
 'ceiling_contacts':('runtime_compat','movement/ceiling_contacts.py'),
 'ceiling_generation':('runtime','movement/ceiling_generation.py'),
 'ceiling_primitives':('runtime','movement/ceiling_profiles.py'),
 'checkpoint_store':('runtime','io/checkpoint_store.py'),
 'climbable_search':('runtime_capability','search/climbable.py'),
 'compiler':('runtime','bridge/compiler.py'),
 'constraint_variants':('runtime','constraints/variants.py'),
 'contact_combinations':('runtime_capability','movement/contact_combinations.py'),
 'contact_profiles':('runtime_capability','movement/contact_profiles.py'),
 'hit_events':('runtime_capability','constraints/hit_events.py'),
 'interactions':('runtime','reachability/service.py'),
 'ladder_ceiling':('runtime_capability','movement/ladder/ceiling.py'),
 'ladder_episodes':('runtime_capability','movement/ladder/episodes.py'),
 'ladder_events':('runtime_capability','movement/ladder/events.py'),
 'ladder_geometry':('runtime_capability','scene/ladders.py'),
 'ladder_ranking':('runtime_capability','search/ladder_ranking.py'),
 'ladder_schedules':('runtime_capability','movement/ladder/schedules.py'),
 'ladder_transfers':('runtime_capability','movement/ladder/transfers.py'),
 'linear_reachability':('runtime','reachability/linear.py'),
 'linkcraft':('runtime_split','io/linkcraft.py; cli/linkcraft.py'),
 'mixed_ladder':('runtime_capability','movement/ladder/contacts.py'),
 'movement_composition':('runtime','movement/program.py'),
 'movement_contacts':('runtime_capability','movement/contacts.py'),
 'movement_entry':('runtime_capability','search/entry.py'),
 'movement_frontier':('runtime','search/frontier.py'),
 'movement_generation':('runtime','movement/generation.py'),
 'movement_problem':('runtime','numerics/builders.py'),
 'movement_search':('runtime','search/movement.py'),
 'movement_units':('runtime','movement/units.py'),
 'obstacle_geometry':('runtime_split','scene/geometry.py; validation/scene.py'),
 'open_ladder':('runtime_capability','movement/ladder/generation.py'),
 'open_movement':('runtime','movement/normal.py'),
 'prefix_viability':('runtime','reachability/viability.py'),
 'ranking':('runtime','search/ranking.py'),
 'reachability':('runtime','reachability/intervals.py'),
 'repair_state':('runtime','search/repair_state.py'),
 'route':('runtime','scene/route.py'),
 'screen':('runtime','reachability/screen.py'),
 'search':('runtime_compat','search/ordered_route.py'),
 'solve':('runtime_cli','cli/ordered_route.py'),
 'solve_blocks':('runtime_cli','cli/blocks.py'),
 'solve_spec':('runtime','search/identity.py'),
 'stepping_generation':('runtime','movement/stepping.py'),
 'terminal_goals':('runtime_compat','search/virtual_goals.py'),
 'vertical_profiles':('runtime','movement/vertical_profiles.py'),
}
CELL_MAP={
 '__init__':('runtime_api','spatial/__init__.py'),
 'anchor_adapter':('runtime','search/anchors/adapter.py'),
 'anchor_guides':('runtime','spatial/anchor_guides.py'),
 'anchor_policy':('runtime','search/anchors/policy.py'),
 'anchor_time_bound':('runtime','search/bounds/remaining.py'),
 'automatic_experiment':('mixed_cli','api.py; cli/solve.py; io/provenance.py'),
 'checkpoint_experiment':('mixed_runtime_research','validation/witness.py; tools/reporting.py; research/checkpoint-guidance/driver.py'),
 'checkpoint_persistence':('runtime','io/search_checkpoint.py'),
 'checkpoint_search':('runtime','search/engine.py'),
 'checkpoints':('runtime_split','spatial/checkpoints.py; search/compat/retention.py'),
 'comparison_goals':('runtime','search/goals.py'),
 'corridor_guards':('runtime_compat','spatial/compat/corridor_guards.py'),
 'decision_windows':('runtime_compat','search/compat/decision_windows.py'),
 'depth_first':('runtime','search/dfs.py'),
 'event_adapter':('runtime','search/events/adapter.py'),
 'event_routes':('runtime','spatial/event_routes.py'),
 'event_search':('runtime','search/events/engine.py'),
 'fixed_clock_experiment':('mixed_cli','cli/fixed_clock.py; research/fixed-clock/driver.py'),
 'geometry':('runtime_plotting_split','spatial/cells.py; tools/plotting/cells.py'),
 'guides':('runtime','spatial/guides.py'),
 'local':('runtime_split','search/work.py; bridge/counting.py; validation/scoped_scene.py'),
 'minimum_runs':('runtime_compat','search/compat/minimum_runs.py'),
 'node_feasibility':('runtime','search/feasibility.py'),
 'preparation_deferrals':('runtime','search/deferrals.py'),
 'regions':('runtime_plotting_split','spatial/regions.py; tools/plotting/regions.py'),
 'route_guards':('runtime_compat','constraints/route_guards.py'),
 'route_progress':('runtime_compat','spatial/compat/progress.py'),
 'route_queue':('runtime_compat','search/compat/corridor_queue.py'),
 'routes':('runtime_compat','spatial/compat/corridors.py'),
 'semantic_adapter':('runtime_compat','search/compat/semantic_adapter.py'),
 'support_order':('runtime_split','search/domain.py; search/compat/support_order.py'),
 'time_progress':('runtime_split','search/completion.py; search/compat/time_progress.py'),
 'transfer_time_bounds':('runtime','search/bounds/flight.py'),
 'turn_observability':('runtime_plotting_split','observability/turns.py; tools/plotting/turns.py'),
 'window_experiment':('mixed_runtime_research','io/reports.py; research/decision-windows/driver.py'),
}
CELL_RESEARCH={
 'comparison_audit','coverage_audit','event_coverage_audit','experiment','failure_diagnostic',
 'fix_validation','generation_diagnostic','repair_diagnostic','repair_resume_experiment',
 'scalar_seed_diagnostic','two_goal_experiment','window_diagnostic'}
IPOPT_MAP={
 '__init__':('runtime_api','__init__.py; api.py'),
 'capture':('runtime','io/capture.py'),
 'constraints':('runtime','numerics/constraints.py'),
 'continuation':('runtime','numerics/continuation.py'),
 'ladder':('runtime','physics/vertical.py'),
 'model':('runtime_split','schema.py; numerics/model.py; physics/horizontal.py; validation/numerical.py'),
 'solve':('runtime_cli','cli/numerical.py'),
 'solver':('runtime','numerics/solver.py'),
 'worker':('runtime','numerics/worker.py'),
}
REVIEW_DOC='docs/STANDALONE_STRUCTURE_REVIEW.md'
AUDIT='autonomy/experiments/structure-audit/'


def target(value):
    return '; '.join(p if p.startswith(('research/','tools/')) else BASE+p for p in value.split('; '))


def module_review(path):
    p=Path(path);parent=str(p.parent);stem=p.stem
    if parent=='autonomy':role,dest=ROOT_MAP[stem]
    elif parent=='autonomy/cell_routes':
        if stem in CELL_RESEARCH:return 'research_driver','research/legacy-cell-routes/'+p.name
        role,dest=CELL_MAP[stem]
    elif parent=='ipopt':role,dest=IPOPT_MAP[stem]
    elif parent=='autonomy/route_guidance':return 'research_prototype','research/route-guidance/prototype/'+p.name
    else:return None
    return role,target(dest)


def classification(path, tracked):
    p=Path(path);parts=p.parts
    if '__pycache__' in parts or p.suffix=='.pyc' or p.name=='.DS_Store':return 'local_cache','ignored cache (rebuildable)'
    if 'build' in parts:
        if p.suffix in ('.class','.jar') or p.name=='compiler.json':return 'local_native_build','build/native/'+p.name
        return 'local_run_or_scratch','runs/legacy-build/'+str(p.relative_to('autonomy/build'))
    if path.endswith('.py'):
        reviewed=module_review(path)
        if reviewed:return reviewed
    if '/tests/' in path:
        if path.startswith('ipopt/'):return 'test','tests/numerics/'+str(p.relative_to('ipopt/tests'))
        return 'test','tests/solver/'+str(p.relative_to('autonomy/tests'))
    if '/fixtures/' in path and not '/experiments/' in path:
        group='numerics' if path.startswith('ipopt/') else 'movement'
        return 'test_fixture','tests/fixtures/'+group+'/'+str(p.relative_to(parts[0]+'/fixtures'))
    if path.startswith('autonomy/experiments/'):
        rest=path.removeprefix('autonomy/experiments/')
        if 'instrumentation' in parts:return 'archived_source_snapshot','research/'+rest
        if p.suffix in ('.py','.java','.cpp'):return 'experiment_driver','research/'+rest
        if p.suffix=='.md' or p.name.startswith('.'):return 'research_document_or_metadata','research/'+rest
        return ('research_evidence' if tracked else 'local_research_result'),'research/'+rest
    if '/java/' in path:return 'native_bridge_source','native/java/'+p.name
    if p.suffix=='.gradle':return 'native_build_script','native/gradle/'+p.name
    if p.name.startswith('requirements'):
        return 'dependency_manifest','pyproject.toml / pinned solver, research or plotting dependency group'
    if path=='AGENTS.md' or path=='README.md':return 'entry_document',path
    if p.suffix=='.md':
        if path.startswith('docs/'):return 'current_documentation',path
        if p.name in ('REPLAY.md','SUPPORTED_FEATURES.md','VERTICAL.md','CAPTURE.md','README.md'):
            return 'capability_documentation','docs/reference/'+parts[0]+'/'+p.name
        return 'design_or_history_document','docs/design/'+str(p)+' (review status for history/research)'
    return 'other_review','manual placement required: '+path


def csv_file(path,rows,fields):
    with path.open('w',newline='') as stream:
        writer=csv.DictWriter(stream,fieldnames=fields,lineterminator='\n')
        writer.writeheader();writer.writerows(rows)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[3])
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();root=args.root.resolve();output=args.output.resolve()
    if output.exists():raise ValueError('Use a fresh audit output directory')
    git_root=Path(subprocess.check_output(['git','rev-parse','--show-toplevel'],cwd=root,text=True).strip())
    prefix=str(root.relative_to(git_root))+'/'
    tracked={p.removeprefix(prefix) for p in subprocess.check_output(['git','ls-files','-z','--',str(root)],cwd=git_root,text=True).split('\0') if p}
    ignored=set(subprocess.check_output(['git','ls-files','--others','--ignored','--exclude-standard','-z'],cwd=root,text=True).split('\0'))
    rows=[]
    for directory,dirs,names in os.walk(root,followlinks=False):
        dirs[:]=sorted(d for d in dirs if not (Path(directory)/d).relative_to(root).as_posix().startswith(AUDIT.rstrip('/')))
        for name in sorted(names):
            p=Path(directory)/name;rel=p.relative_to(root).as_posix()
            if rel==REVIEW_DOC:continue
            role,dest=classification(rel,rel in tracked)
            rows.append(dict(path=rel,tracked=rel in tracked,ignored=rel in ignored,
                bytes=p.lstat().st_size,suffix=p.suffix or '<none>',role=role,proposed_destination=dest,
                symlink=p.is_symlink()))
    rows.sort(key=lambda r:r['path'])
    assert len({r['path'] for r in rows})==len(rows)
    tracked_rows=[r for r in rows if r['tracked']]
    assert {r['path'] for r in tracked_rows}=={p for p in tracked if not p.startswith(AUDIT) and p!=REVIEW_DOC}
    py=[r for r in tracked_rows if r['suffix']=='.py'];by_module={};trees={};module_rows=[]
    for row in py:
        path=row['path'];tree=ast.parse((root/path).read_text(),filename=path);trees[path]=tree
        module='standalone.'+path[:-3].replace('/','.')
        if module.endswith('.__init__'):module=module[:-9]
        by_module[module]=path
    graph=defaultdict(set);edges=[]
    for module,path in sorted(by_module.items()):
        tree=trees[path];package=module if Path(path).name=='__init__.py' else module.rpartition('.')[0]
        for node in ast.walk(tree):
            if isinstance(node,ast.Import):requests=[(a.name,'') for a in node.names]
            elif isinstance(node,ast.ImportFrom):
                if node.level:
                    base=package.split('.')[:len(package.split('.'))-node.level+1]
                    base='.'.join(base+([node.module] if node.module else []))
                else:base=node.module or ''
                requests=[(base,'.'.join(a.name for a in node.names))]
                requests += [(base+'.'+a.name,a.name) for a in node.names if base+'.'+a.name in by_module]
            else:continue
            for imported,symbols in requests:
                if imported in by_module:
                    dest=by_module[imported];graph[path].add(dest)
                    edges.append(dict(importer=path,line=node.lineno,imported=dest,symbols=symbols,
                        importer_role=classification(path,True)[0],imported_role=classification(dest,True)[0]))
    def closure(seeds,initializers=False):
        reached=set();pending=list(seeds)
        while pending:
            p=pending.pop()
            if p in reached:continue
            reached.add(p);pending.extend(graph[p]-reached)
            if initializers:
                for ancestor in (root/p).relative_to(root).parents:
                    init=str(ancestor/'__init__.py')
                    if init in trees and init!=p:pending.append(init)
        return reached
    anchor_seeds=['autonomy/cell_routes/checkpoint_search.py']
    anchor=closure(anchor_seeds);initialized=closure(anchor_seeds,True)
    broad=closure(anchor_seeds+['autonomy/block_search.py','autonomy/search.py','ipopt/solver.py','ipopt/capture.py'],True)
    inbound=defaultdict(set)
    for e in edges:inbound[e['imported']].add(e['importer'])
    for row in py:
        path=row['path'];tree=trees[path]
        symbols=[n.name for n in tree.body if isinstance(n,(ast.FunctionDef,ast.AsyncFunctionDef,ast.ClassDef))]
        module_rows.append(dict(path=path,role=row['role'],proposed_destination=row['proposed_destination'],
            lines=len((root/path).read_text().splitlines()),known_subprocess_entry=path=='ipopt/worker.py',anchor_static_closure=path in anchor,
            anchor_with_package_initializers=path in initialized,supported_api_static_closure=path in broad,
            importers=len(inbound[path]),runtime_importers=';'.join(sorted(p for p in inbound[path] if classification(p,True)[0].startswith(('runtime','mixed')))),
            has_main_guard=any(isinstance(n,ast.If) and '__name__' in ast.unparse(n.test) for n in tree.body),
            top_level_symbols=';'.join(symbols),docstring=(ast.get_docstring(tree) or '').split('\n')[0]))
    def totals(selection):
        groups={}
        for row in selection:
            group=groups.setdefault(row['role'],dict(files=0,bytes=0));group['files']+=1;group['bytes']+=row['bytes']
        return groups
    directory_rows=[]
    for directory in sorted({str(Path(r['path']).parent) for r in rows}):
        own=[r for r in rows if str(Path(r['path']).parent)==directory]
        directory_rows.append(dict(directory=directory,files=len(own),bytes=sum(r['bytes'] for r in own),tracked=sum(r['tracked'] for r in own)))
    reviewed=[r for r in module_rows if module_review(r['path']) is not None]
    cross=[e for e in edges if e['importer_role'].startswith(('runtime','mixed')) and e['imported_role'].startswith(('mixed','research'))]
    summary=dict(sourceRevision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),
        root=str(root),dirtyPathsBefore=subprocess.check_output(['git','diff','--name-only'],cwd=root,text=True).splitlines(),
        scope='All physical files under standalone, including ignored files; no followed directory symlinks. Audit bundle and review document excluded to avoid self-inventory.',
        physicalFiles=len(rows),physicalBytes=sum(r['bytes'] for r in rows),trackedFiles=len(tracked_rows),trackedBytes=sum(r['bytes'] for r in tracked_rows),
        trackedPython=len(py),trackedByRole=totals(tracked_rows),physicalByRole=totals(rows),
        codeOutsideTestsAndExperiments=len(reviewed),reviewedCodeByRole=dict(Counter(r['role'] for r in reviewed)),
        anchorStaticClosure=len(anchor),anchorWithInitializers=len(initialized),supportedApiStaticClosure=len(broad),
        runtimeToMixedOrResearchEdges=cross,
        knownDynamicEntries=[dict(path='ipopt/worker.py',invoker='ipopt/solver.py:_worker_command',
            reason='Launched by file path/subprocess; static import closure cannot identify this required runtime entry.')],
        testsWithExperimentPathReferences=[r['path'] for r in module_rows if r['role']=='test' and
            'experiments' in (root/r['path']).read_text()],
        testToTestImports=[e for e in edges if e['importer_role']=='test' and e['imported_role']=='test'],
        modulesUsingFileRelativePaths=[r['path'] for r in module_rows if '__file__' in (root/r['path']).read_text()],
        staticAnalysisLimit='AST includes all conditional/lazy imports and whole modules; it is not dynamic/function reachability, a completeness proof, or a dead-code deletion test.',
        largestTracked=[dict(path=r['path'],bytes=r['bytes']) for r in sorted(tracked_rows,key=lambda r:-r['bytes'])[:15]],
        moduleDestinations='Proposed, not approved moves. Semicolon-separated destinations require extraction before relocation. Compatibility/capability code is not automatically research.')
    output.mkdir(parents=True)
    csv_file(output/'files.csv',rows,list(rows[0]))
    csv_file(output/'modules.csv',module_rows,list(module_rows[0]))
    csv_file(output/'imports.csv',sorted(edges,key=lambda e:(e['importer'],e['line'],e['imported'])),list(edges[0]))
    csv_file(output/'directories.csv',directory_rows,list(directory_rows[0]))
    (output/'summary.json').write_text(json.dumps(summary,indent=2,allow_nan=False)+'\n')
    print(json.dumps({k:summary[k] for k in ('physicalFiles','physicalBytes','trackedFiles','trackedBytes','trackedPython','codeOutsideTestsAndExperiments','reviewedCodeByRole','anchorStaticClosure','anchorWithInitializers')},indent=2))


if __name__=='__main__':main()
