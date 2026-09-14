package com.company.orchestrator.solver;
import static com.company.orchestrator.solver.PlanningData.*;
import java.util.*;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
@Service
public class CandidateService {
    public List<Candidate> candidates(Input input,Work task) {
        var result=new ArrayList<Candidate>();
        for(var p:input.people()) {
            if(!"ACTIVE".equals(p.status())) continue;
            if(task.needs().stream().anyMatch(n -> "REQUIRED".equals(n.type()) && p.skills().getOrDefault(n.skillId(),0)<n.minLevel())) continue;
            int allocation=(int)Math.ceil(task.hours()*50000.0/(days(task.start(),task.end()).size()*p.weeklyHours()));
            if(allocation<=0 || allocation>10000) continue;
            var remaining=new TreeMap<LocalDate,Integer>();
            for(var day:days(task.start(),task.end())) {
                int capacity=p.capacity();
                for(var w:input.windows()) if(w.employeeId()==p.id() && !day.isBefore(w.start()) && !day.isAfter(w.end()))
                    capacity=Math.min(capacity,"AVAILABLE".equals(w.type())?w.capacity():0);
                for(var b:input.bookings()) if(b.employeeId()==p.id() && !day.isBefore(b.start()) && !day.isAfter(b.end())) capacity-=b.capacity();
                remaining.put(day,Math.max(0,capacity));
            }
            if(remaining.values().stream().anyMatch(v -> v<allocation)) continue;
            double total=task.needs().stream().mapToDouble(Need::weight).sum();
            int score=total==0?100:(int)Math.round(100*task.needs().stream().mapToDouble(n -> n.weight()*Math.min(1.0,p.skills().getOrDefault(n.skillId(),0)/(double)n.minLevel())).sum()/total);
            result.add(new Candidate(p.id(),p.name(),score,allocation,remaining));
        }
        result.sort(Comparator.comparingInt(Candidate::score).reversed().thenComparingLong(Candidate::employeeId));
        return result;
    }
}
