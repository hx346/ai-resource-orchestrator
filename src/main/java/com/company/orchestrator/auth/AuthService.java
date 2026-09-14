package com.company.orchestrator.auth;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.company.orchestrator.common.exception.*;
@Service
public class AuthService implements UserDetailsService, ApplicationRunner {
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    private final String bootstrapPassword;
    public AuthService(JdbcTemplate db, PasswordEncoder encoder, @Value("${app.admin-password:}") String password) {
        this.db=db; this.encoder=encoder; this.bootstrapPassword=password;
    }
    @Override public UserDetails loadUserByUsername(String username) {
        return db.query("select username,password_hash,role,status from sys_user where username=?", (rs,n) ->
            User.withUsername(rs.getString(1)).password(rs.getString(2)).roles(rs.getString(3)).disabled(!"ACTIVE".equals(rs.getString(4))).build(),username)
            .stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
    @Override @Transactional(rollbackFor=Exception.class) public void run(ApplicationArguments args) {
        if (!bootstrapPassword.isBlank()) {
            if (bootstrapPassword.length()<12) throw new IllegalArgumentException("ADMIN_PASSWORD requires at least 12 characters");
            db.update("insert into sys_user(username,password_hash,role) values (?,?,'ADMIN') on conflict(username) do nothing","admin",encoder.encode(bootstrapPassword));
        }
    }
    public List<Map<String,Object>> users() { return db.queryForList("select id,username,role,status from sys_user order by id"); }
    @Transactional(rollbackFor=Exception.class) public void create(String username,String password,String role) {
        if (!Set.of("ADMIN","PROJECT_MANAGER","DEPARTMENT_MANAGER","EMPLOYEE").contains(role)) throw new BusinessException(ErrorCode.ROLE_INVALID);
        db.update("insert into sys_user(username,password_hash,role) values (?,?,?)",username,encoder.encode(password),role);
    }
}
