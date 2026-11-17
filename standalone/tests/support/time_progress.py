"""Shared test builders extracted from tests.unit.search.test_time_progress."""
def label(name,tick,score,*,clock='clock:10',goal='landing:G'):
    state=dict(motion=dict(schedule=['A']*10,phase='same',jumps=1,runs=0),forwardVelocity=0.,speed=0.)
    record=dict(id=name,required_spatial=[goal],required_clocks=[clock],
        spatial_results={goal:dict(status='feasible' if tick is not None else 'unknown',ticks=tick)},
        clock_results={clock:dict(status='feasible' if score is not None else 'unknown',objective=score,state=state)})
    return record,dict(name=name)
